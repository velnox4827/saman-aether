package com.saman.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class AetherService : Service() {
    companion object {
        const val ACTION_START = "com.saman.tunnel.START"
        const val ACTION_STOP = "com.saman.tunnel.STOP"
        const val EXTRA_MODE = "mode"
        const val PREFS = "saman_tunnel"
        const val KEY_STATUS = "status"
        const val KEY_MODE = "mode"
        const val KEY_LAST_MODE = "last_mode"
        const val KEY_JOB_ID = "native_job_id"
        private const val CHANNEL_ID = "saman_tunnel_core"
        private const val NOTIFICATION_ID = 1819
        private const val SOCKS_PORT = 1819
        private const val HTTP_PORT = 1820
    }

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var jobId: Long = 0L
    @Volatile private var generation: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val remembered = prefs().getLong(KEY_JOB_ID, 0L)
        if (remembered != 0L) {
            val recovered = runCatching {
                val reply = JSONObject(NativeBridge.pollJob(remembered))
                reply.optBoolean("ok") && reply.optString("state") == "running"
            }.getOrDefault(false)

            if (recovered) {
                jobId = remembered
                LogStore.append(this, "SERVICE", "Recovered native job id=$remembered")
            } else {
                runCatching { NativeBridge.freeJob(remembered) }
                clearRememberedJob(remembered)
                LogStore.append(this, "SERVICE", "Discarded stale native job id=$remembered")
            }
        }

        LogStore.append(this, "SERVICE", "Service created jobId=$jobId")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCore()
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: "WG"

                prefs().edit()
                    .putString(KEY_LAST_MODE, mode)
                    .apply()

                LogStore.append(this, "SERVICE", "Start command mode=$mode")
                beginForeground("Preparing ${modeLabel(mode)}…")
                requestStart(mode)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogStore.append(this, "SERVICE", "Service destroying; jobId=$jobId")
        generation++
        val id = jobId
        if (id != 0L) {
            runCatching { NativeBridge.cancelJob(id) }
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun requestStart(mode: String) {
        val myGeneration = ++generation
        val previous = jobId

        if (previous != 0L) {
            setState("Switching mode…", mode)
            updateNotification("Switching to ${modeLabel(mode)}…")
        } else {
            setState("Starting…", mode)
            updateNotification("Starting ${modeLabel(mode)}…")
        }

        executor.execute {
            try {
                if (previous != 0L) {
                    LogStore.append(this, "CORE", "Auto-reset previous job=$previous")
                    if (!cancelAndAwait(previous, 10_000L)) {
                        fail("Previous core is still stopping. Tap STOP once and retry.", mode)
                        return@execute
                    }
                    if (!waitForProxyPortsToClose(6_000L)) {
                        fail("Old proxy ports did not close after core stopped.", mode)
                        return@execute
                    }
                } else if (isPortOpen(SOCKS_PORT) || isPortOpen(HTTP_PORT)) {
                    fail("Port 1819/1820 is already in use. Stop the other local proxy first.", mode)
                    return@execute
                }

                if (generation != myGeneration) return@execute
                launchCore(mode, myGeneration)
            } catch (t: Throwable) {
                LogStore.append(this, "EXCEPTION", t.stackTraceToString())
                fail(t.message ?: t.javaClass.simpleName, mode)
            }
        }
    }

    private fun launchCore(mode: String, myGeneration: Long) {
        val args = JSONArray(argumentsFor(mode)).toString()
        LogStore.append(this, "CORE", "Starting mode=$mode args=$args")

        val rawStarted = NativeBridge.startCore(args, filesDir.absolutePath)
        LogStore.append(this, "NATIVE", "startCore reply=$rawStarted")
        val started = JSONObject(rawStarted)

        if (!started.optBoolean("ok")) {
            fail(started.optString("error", "Aether core failed to start"), mode)
            return
        }

        val id = started.optLong("job", 0L)
        if (id == 0L) {
            fail("Core did not return a job id", mode)
            return
        }

        rememberJob(id)
        LogStore.append(this, "CORE", "Core job started id=$id")
        setState("Connecting…", mode)
        updateNotification("Connecting ${modeLabel(mode)}…")

        // Faster app-side readiness detection while preserving the
        // original 120-second maximum startup window.
        for (i in 0 until 480) {
            if (generation != myGeneration || jobId != id) return
            val rawPoll = NativeBridge.pollJob(id)
            val polled = JSONObject(rawPoll)

            if (polled.optString("state") == "done") {
                LogStore.append(this, "NATIVE", "Job ended before proxy ready: $rawPoll")
                val result = polled.optJSONObject("result")
                freeFinishedJob(id)
                fail(result?.optString("error") ?: "Core stopped before local proxy became ready", mode)
                return
            }

            if (isPortOpen(SOCKS_PORT)) {
                val httpReady = isPortOpen(HTTP_PORT)
                LogStore.append(this, "CORE", "SOCKS5 ready; HTTP ready=$httpReady")
                setConnectedState(mode, httpReady)
                monitor(id, mode, myGeneration)
                return
            }

            Thread.sleep(250)
        }

        cancelAndAwait(id, 10_000L)
        fail("SOCKS5 did not become ready", mode)
    }

    private fun monitor(id: Long, mode: String, myGeneration: Long) {
        var consecutiveSocksFailures = 0
        var healthWarningShown = false

        while (generation == myGeneration && jobId == id) {
            try {
                val rawPoll = NativeBridge.pollJob(id)
                val polled = JSONObject(rawPoll)

                if (polled.optString("state") == "done") {
                    LogStore.append(this, "NATIVE", "Job done: $rawPoll")
                    val result = polled.optJSONObject("result")
                    val error = result?.optString("error")
                    if (!error.isNullOrBlank()) fail(error, mode)
                    else {
                        setState("Stopped", mode)
                        updateNotification("Stopped")
                    }
                    freeFinishedJob(id)
                    stopSelf()
                    return
                }

                if (isPortOpen(SOCKS_PORT)) {
                    if (consecutiveSocksFailures > 0) {
                        LogStore.append(this, "HEALTH", "SOCKS5 recovered after $consecutiveSocksFailures failed health check(s)")
                    }
                    consecutiveSocksFailures = 0
                    if (healthWarningShown) {
                        healthWarningShown = false
                        setConnectedState(mode, isPortOpen(HTTP_PORT))
                    }
                } else {
                    consecutiveSocksFailures++
                    LogStore.append(this, "HEALTH", "SOCKS5 health check failed $consecutiveSocksFailures/3")
                    if (consecutiveSocksFailures >= 3 && !healthWarningShown) {
                        healthWarningShown = true
                        setState("Connection unstable — checking SOCKS5", mode)
                        updateNotification("${modeLabel(mode)} active — checking SOCKS5")
                    }
                }

                Thread.sleep(2000)
            } catch (t: Throwable) {
                LogStore.append(this, "EXCEPTION", "monitor: ${t.stackTraceToString()}")
                cancelAndAwait(id, 8_000L)
                fail(t.message ?: "Monitoring failed", mode)
                return
            }
        }
    }

    private fun setConnectedState(mode: String, httpReady: Boolean) {
        val status = if (httpReady) "Connected — SOCKS5 :1819 + HTTP :1820" else "Connected — SOCKS5 :1819"
        setState(status, mode)
        updateNotification(
            if (httpReady) "${modeLabel(mode)} connected — SOCKS5 + HTTP"
            else "${modeLabel(mode)} connected — SOCKS5"
        )
    }

    private fun stopCore() {
        val remembered = prefs().getLong(KEY_JOB_ID, 0L)
        val id = if (jobId != 0L) jobId else remembered
        val mode = prefs().getString(KEY_MODE, "") ?: ""

        generation++
        LogStore.append(this, "CORE", "Stop requested jobId=$id remembered=$remembered")
        setState("Stopping…", mode)
        updateNotification("Stopping ${modeLabel(mode)}…")

        executor.execute {
            val coreStopped = if (id != 0L) {
                cancelAndAwait(id, 12_000L)
            } else {
                true
            }

            val portsClosed = waitForProxyPortsToClose(
                if (coreStopped) 6_000L else 2_000L
            )

            if (coreStopped && portsClosed) {
                clearRememberedJob(id)
                setState("Stopped", "")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                val reason = when {
                    !coreStopped -> "Native core did not finish stopping yet"
                    else -> "Local proxy port is still open after core stopped"
                }
                LogStore.append(
                    this,
                    "ERROR",
                    "Stop incomplete jobId=$id coreStopped=$coreStopped portsClosed=$portsClosed"
                )
                setState("Error: $reason. Tap STOP again.", mode)
                updateNotification("Stop incomplete — open Saman Tunnel")
            }
        }
    }

    private fun cancelAndAwait(id: Long, timeoutMs: Long): Boolean {
        if (id == 0L) return true

        runCatching {
            LogStore.append(this, "NATIVE", "cancelJob reply=${NativeBridge.cancelJob(id)}")
        }.onFailure {
            LogStore.append(this, "NATIVE", "cancelJob failed: ${it.stackTraceToString()}")
        }

        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val raw = runCatching { NativeBridge.pollJob(id) }.getOrElse {
                LogStore.append(this, "NATIVE", "pollJob during stop failed: ${it.message}")
                return false
            }

            val reply = runCatching { JSONObject(raw) }.getOrNull()
            if (reply == null) {
                LogStore.append(this, "NATIVE", "pollJob returned invalid JSON: $raw")
                return false
            }

            if (!reply.optBoolean("ok")) {
                val error = reply.optString("error")
                if (error.contains("there is no job", ignoreCase = true)) {
                    clearRememberedJob(id)
                    return true
                }
                LogStore.append(this, "NATIVE", "pollJob stop error: $raw")
                return false
            }

            if (reply.optString("state") == "done") {
                freeFinishedJob(id)
                return true
            }

            Thread.sleep(125)
        }

        LogStore.append(this, "NATIVE", "Timed out waiting for job $id to stop")
        return false
    }

    private fun freeFinishedJob(id: Long) {
        runCatching {
            LogStore.append(this, "NATIVE", "freeJob reply=${NativeBridge.freeJob(id)}")
        }.onFailure {
            LogStore.append(this, "NATIVE", "freeJob failed: ${it.stackTraceToString()}")
        }
        clearRememberedJob(id)
    }

    private fun rememberJob(id: Long) {
        jobId = id
        prefs().edit().putLong(KEY_JOB_ID, id).apply()
    }

    private fun clearRememberedJob(id: Long) {
        if (id == 0L || jobId == id) {
            jobId = 0L
        }
        val stored = prefs().getLong(KEY_JOB_ID, 0L)
        if (id == 0L || stored == id) {
            prefs().edit().remove(KEY_JOB_ID).apply()
        }
    }

    private fun fail(message: String, mode: String) {
        LogStore.append(this, "ERROR", "mode=$mode message=$message")
        setState("Error: $message", mode)
        updateNotification("Error — open Saman Tunnel")
    }

    private fun argumentsFor(mode: String): List<String> {
        val commonProxyArgs = listOf(
            "--bind", "127.0.0.1:$SOCKS_PORT",
            "--http-proxy", "127.0.0.1:$HTTP_PORT",
            "--reconnect-secs", "1"
        )

        return when (mode.uppercase()) {
            "MASQUE_H2" -> listOf("--masque", "--h2", "-4") + commonProxyArgs + listOf(
                "--scan", "balanced", "--noize", "firewall", "--quick-reconnect"
            )
            "MASQUE_H3", "MASQUE" -> listOf("--masque", "-4") + commonProxyArgs + listOf(
                "--scan", "balanced", "--noize", "firewall", "--quick-reconnect"
            )
            "GOOL" -> listOf("--gool", "-4") + commonProxyArgs + listOf(
                "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect"
            )
            else -> listOf("--wg", "-4") + commonProxyArgs + listOf(
                "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect"
            )
        }
    }

    private fun modeLabel(mode: String): String = when (mode.uppercase()) {
        "MASQUE_H2" -> "MASQUE H2"
        "MASQUE_H3", "MASQUE" -> "MASQUE H3"
        "WG" -> "WG"
        "GOOL" -> "GOOL"
        else -> mode
    }

    private fun isPortOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 600) }
        true
    } catch (_: Throwable) {
        false
    }

    private fun waitForProxyPortsToClose(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!isPortOpen(SOCKS_PORT) && !isPortOpen(HTTP_PORT)) return true
            Thread.sleep(500)
        }
        return !isPortOpen(SOCKS_PORT) && !isPortOpen(HTTP_PORT)
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun setState(status: String, mode: String) {
        val p = prefs()
        val oldStatus = p.getString(KEY_STATUS, null)
        val oldMode = p.getString(KEY_MODE, null)
        p.edit().putString(KEY_STATUS, status).putString(KEY_MODE, mode).apply()
        if (oldStatus != status || oldMode != mode) {
            LogStore.append(this, "STATE", "mode=$mode status=$status")
        }

        SamanTunnelWidget.updateAll(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Saman Tunnel Core", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun beginForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else Notification.Builder(this)

        return builder
            .setContentTitle(if (packageName.endsWith(".beta")) "Saman Tunnel Beta" else "Saman Tunnel")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_saman_tunnel)
            .setColor(Color.rgb(68, 145, 255))
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
