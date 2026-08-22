package com.saman.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
        private const val CHANNEL_ID = "saman_tunnel_core"
        private const val NOTIFICATION_ID = 1819
    }

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var jobId: Long = 0L
    @Volatile private var generation: Long = 0L

    override fun onCreate() {
        super.onCreate()
        LogStore.append(this, "SERVICE", "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCore()
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: "WG"
                LogStore.append(this, "SERVICE", "Start command mode=$mode")
                beginForeground("Starting $mode…")
                startCore(mode)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogStore.append(this, "SERVICE", "Service destroying; jobId=$jobId")
        generation++
        if (jobId != 0L) {
            runCatching { NativeBridge.cancelJob(jobId) }
                .onFailure { LogStore.append(this, "NATIVE", "cancel onDestroy failed: ${it.stackTraceToString()}") }
            runCatching { NativeBridge.freeJob(jobId) }
                .onFailure { LogStore.append(this, "NATIVE", "free onDestroy failed: ${it.stackTraceToString()}") }
            jobId = 0L
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startCore(mode: String) {
        val myGeneration = ++generation
        setState("Starting…", mode)

        val previous = jobId
        if (previous != 0L) {
            LogStore.append(this, "CORE", "Cancelling previous job=$previous")
            runCatching { NativeBridge.cancelJob(previous) }
            runCatching { NativeBridge.freeJob(previous) }
            jobId = 0L
        }

        executor.execute {
            try {
                val args = JSONArray(argumentsFor(mode)).toString()
                LogStore.append(this, "CORE", "Starting mode=$mode args=$args")
                val rawStarted = NativeBridge.startCore(args, filesDir.absolutePath)
                LogStore.append(this, "NATIVE", "startCore reply=$rawStarted")
                val started = JSONObject(rawStarted)

                if (!started.optBoolean("ok")) {
                    fail(started.optString("error", "Aether core failed to start"), mode)
                    return@execute
                }

                val id = started.optLong("job", 0L)
                if (id == 0L) {
                    fail("Core did not return a job id", mode)
                    return@execute
                }

                jobId = id
                LogStore.append(this, "CORE", "Core job started id=$id")
                setState("Connecting…", mode)
                updateNotification("Connecting $mode…")

                for (i in 0 until 120) {
                    if (generation != myGeneration || jobId != id) return@execute
                    val rawPoll = NativeBridge.pollJob(id)
                    val polled = JSONObject(rawPoll)
                    if (polled.optString("state") == "done") {
                        LogStore.append(this, "NATIVE", "Job ended before SOCKS ready: $rawPoll")
                        val result = polled.optJSONObject("result")
                        fail(result?.optString("error") ?: "Core stopped before SOCKS5 became ready", mode)
                        return@execute
                    }

                    if (isSocksReady()) {
                        LogStore.append(this, "CORE", "SOCKS5 ready on 127.0.0.1:1819")
                        setState("Connected — SOCKS5 127.0.0.1:1819", mode)
                        updateNotification("$mode connected — 127.0.0.1:1819")
                        monitor(id, mode, myGeneration)
                        return@execute
                    }
                    Thread.sleep(1000)
                }
                fail("SOCKS5 did not become ready", mode)
            } catch (t: Throwable) {
                LogStore.append(this, "EXCEPTION", t.stackTraceToString())
                fail(t.message ?: t.javaClass.simpleName, mode)
            }
        }
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

                    if (!error.isNullOrBlank()) {
                        fail(error, mode)
                    } else {
                        setState("Stopped", mode)
                        updateNotification("Stopped")
                    }

                    jobId = 0L
                    stopSelf()
                    return
                }

                if (isSocksReady()) {
                    if (consecutiveSocksFailures > 0) {
                        LogStore.append(
                            this,
                            "HEALTH",
                            "SOCKS5 recovered after $consecutiveSocksFailures failed health check(s)"
                        )
                    }

                    consecutiveSocksFailures = 0

                    if (healthWarningShown) {
                        healthWarningShown = false
                        setState("Connected — SOCKS5 127.0.0.1:1819", mode)
                        updateNotification("$mode connected — 127.0.0.1:1819")
                    }
                } else {
                    consecutiveSocksFailures++

                    LogStore.append(
                        this,
                        "HEALTH",
                        "SOCKS5 health check failed $consecutiveSocksFailures/3"
                    )

                    // One or two brief local failures do not prove a core reconnect.
                    if (consecutiveSocksFailures >= 3 && !healthWarningShown) {
                        healthWarningShown = true
                        setState("Connection unstable — checking SOCKS5", mode)
                        updateNotification("$mode active — checking SOCKS5")
                    }
                }

                Thread.sleep(2000)
            } catch (t: Throwable) {
                LogStore.append(this, "EXCEPTION", "monitor: ${t.stackTraceToString()}")
                fail(t.message ?: "Monitoring failed", mode)
                return
            }
        }
    }

    private fun stopCore() {
        val id = jobId
        generation++
        LogStore.append(this, "CORE", "Stop requested jobId=$id")
        setState("Stopping…", prefs().getString(KEY_MODE, "") ?: "")

        executor.execute {
            if (id != 0L) {
                runCatching { LogStore.append(this, "NATIVE", "cancelJob reply=${NativeBridge.cancelJob(id)}") }
                Thread.sleep(350)
                runCatching { LogStore.append(this, "NATIVE", "freeJob reply=${NativeBridge.freeJob(id)}") }
            }
            jobId = 0L
            setState("Stopped", "")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fail(message: String, mode: String) {
        LogStore.append(this, "ERROR", "mode=$mode message=$message")
        setState("Error: $message", mode)
        updateNotification("Error — open Saman Tunnel")
    }

    private fun argumentsFor(mode: String): List<String> = when (mode.uppercase()) {
        "MASQUE" -> listOf("--masque", "-4", "--bind", "127.0.0.1:1819", "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
        "GOOL" -> listOf("--gool", "-4", "--bind", "127.0.0.1:1819", "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
        else -> listOf("--wg", "-4", "--bind", "127.0.0.1:1819", "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
    }

    private fun isSocksReady(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", 1819), 1000) }
        true
    } catch (_: Throwable) { false }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun setState(status: String, mode: String) {
        val p = prefs()
        val oldStatus = p.getString(KEY_STATUS, null)
        val oldMode = p.getString(KEY_MODE, null)
        p.edit().putString(KEY_STATUS, status).putString(KEY_MODE, mode).apply()
        if (oldStatus != status || oldMode != mode) {
            LogStore.append(this, "STATE", "mode=$mode status=$status")
        }
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
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("Saman Tunnel").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(true).setContentIntent(pending).build()
    }
}
