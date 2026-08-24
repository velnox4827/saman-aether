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
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
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

        private const val CHANNEL_ID = "saman_tunnel_core"
        private const val NOTIFICATION_ID = 1819
        private const val SOCKS_PORT = 1819
        private const val HTTP_PORT = 1820
    }

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var jobId: Long = 0L
    @Volatile private var generation: Long = 0L
    @Volatile private var currentMode: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LogStore.append(
            this,
            "SERVICE",
            "Core service created pid=${Process.myPid()}"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCore()

            ACTION_START -> {
                val mode =
                    intent.getStringExtra(EXTRA_MODE)
                        ?.ifBlank { "WG" }
                        ?: "WG"

                if (jobId != 0L) {
                    LogStore.append(
                        this,
                        "SERVICE",
                        "Ignoring duplicate start mode=$mode jobId=$jobId"
                    )
                    return START_NOT_STICKY
                }

                currentMode = mode
                LogStore.append(
                    this,
                    "SERVICE",
                    "Start command mode=$mode pid=${Process.myPid()}"
                )
                beginForeground("Preparing ${modeLabel(mode)}…")
                requestStart(mode)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        generation++
        val id = jobId
        if (id != 0L) {
            runCatching { NativeBridge.cancelJob(id) }
        }

        LogStore.append(
            this,
            "SERVICE",
            "Core service destroyed pid=${Process.myPid()} jobId=$id"
        )

        executor.shutdownNow()
        super.onDestroy()
    }

    private fun requestStart(mode: String) {
        val myGeneration = ++generation
        setState("Starting…", mode)
        updateNotification("Starting ${modeLabel(mode)}…")

        executor.execute {
            try {
                if (isPortInUse(SOCKS_PORT) || isPortInUse(HTTP_PORT)) {
                    fail(
                        "Port 1819/1820 is already in use before core start.",
                        mode
                    )
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
            fail(
                started.optString("error", "Aether core failed to start"),
                mode
            )
            return
        }

        val id = started.optLong("job", 0L)
        if (id == 0L) {
            fail("Core did not return a job id", mode)
            return
        }

        jobId = id
        LogStore.append(
            this,
            "CORE",
            "Core job started id=$id pid=${Process.myPid()}"
        )

        setState("Connecting…", mode)
        updateNotification("Connecting ${modeLabel(mode)}…")

        // 480 * 250 ms = the original 120-second ceiling.
        for (i in 0 until 480) {
            if (generation != myGeneration || jobId != id) return

            val rawPoll = NativeBridge.pollJob(id)
            val polled = JSONObject(rawPoll)

            if (polled.optString("state") == "done") {
                val result = polled.optJSONObject("result")
                LogStore.append(
                    this,
                    "NATIVE",
                    "Job ended before proxy ready: $rawPoll"
                )
                jobId = 0L
                fail(
                    result?.optString("error")
                        ?: "Core stopped before local proxy became ready",
                    mode
                )
                return
            }

            if (isPortInUse(SOCKS_PORT)) {
                val httpReady = isPortInUse(HTTP_PORT)
                LogStore.append(
                    this,
                    "CORE",
                    "SOCKS5 bound; HTTP bound=$httpReady"
                )
                setConnectedState(mode, httpReady)
                monitor(id, mode, myGeneration)
                return
            }

            Thread.sleep(250)
        }

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
                    jobId = 0L

                    if (!error.isNullOrBlank()) {
                        fail(error, mode)
                    } else {
                        setState("Stopped", "")
                        updateNotification("Stopped")
                        hardExitCoreProcess(150L)
                    }
                    return
                }

                if (isPortInUse(SOCKS_PORT)) {
                    if (consecutiveSocksFailures > 0) {
                        LogStore.append(
                            this,
                            "HEALTH",
                            "SOCKS5 binding recovered after $consecutiveSocksFailures failed check(s)"
                        )
                    }

                    consecutiveSocksFailures = 0

                    if (healthWarningShown) {
                        healthWarningShown = false
                        setConnectedState(mode, isPortInUse(HTTP_PORT))
                    }
                } else {
                    consecutiveSocksFailures++

                    LogStore.append(
                        this,
                        "HEALTH",
                        "SOCKS5 binding check failed $consecutiveSocksFailures/3"
                    )

                    if (consecutiveSocksFailures >= 3 && !healthWarningShown) {
                        healthWarningShown = true
                        setState(
                            "Connection unstable — checking SOCKS5",
                            mode
                        )
                        updateNotification(
                            "${modeLabel(mode)} active — checking SOCKS5"
                        )
                    }
                }

                Thread.sleep(2000)
            } catch (t: Throwable) {
                LogStore.append(
                    this,
                    "EXCEPTION",
                    "monitor: ${t.stackTraceToString()}"
                )
                fail(t.message ?: "Monitoring failed", mode)
                return
            }
        }
    }

    private fun setConnectedState(mode: String, httpReady: Boolean) {
        val status =
            if (httpReady) {
                "Connected — SOCKS5 :1819 + HTTP :1820"
            } else {
                "Connected — SOCKS5 :1819"
            }

        setState(status, mode)

        updateNotification(
            if (httpReady) {
                "${modeLabel(mode)} connected — SOCKS5 + HTTP"
            } else {
                "${modeLabel(mode)} connected — SOCKS5"
            }
        )
    }

    private fun stopCore() {
        val id = jobId
        val mode = currentMode

        generation++
        LogStore.append(
            this,
            "CORE",
            "Hard stop requested jobId=$id pid=${Process.myPid()}"
        )

        setState("Stopping…", mode)
        updateNotification("Stopping ${modeLabel(mode)}…")

        Thread {
            if (id != 0L) {
                runCatching {
                    val reply = NativeBridge.cancelJob(id)
                    LogStore.append(
                        this,
                        "NATIVE",
                        "cancelJob reply=$reply"
                    )
                }.onFailure {
                    LogStore.append(
                        this,
                        "NATIVE",
                        "cancelJob failed: ${it.stackTraceToString()}"
                    )
                }
            }

            Thread.sleep(250)
            jobId = 0L
            currentMode = ""
            setState("Stopped", "")
            stopForeground(STOP_FOREGROUND_REMOVE)

            Thread.sleep(150)
            LogStore.append(
                this,
                "SERVICE",
                "Killing isolated core process pid=${Process.myPid()}"
            )

            Process.killProcess(Process.myPid())
        }.start()
    }

    private fun fail(message: String, mode: String) {
        LogStore.append(
            this,
            "ERROR",
            "mode=$mode message=$message"
        )
        setState("Error: $message", mode)
        updateNotification("Error — open Saman Tunnel")

        val id = jobId
        if (id != 0L) {
            Thread {
                runCatching { NativeBridge.cancelJob(id) }
                Thread.sleep(250)
                jobId = 0L
                stopForeground(STOP_FOREGROUND_REMOVE)
                Thread.sleep(100)
                Process.killProcess(Process.myPid())
            }.start()
        }
    }

    private fun argumentsFor(mode: String): List<String> {
        val commonProxyArgs = listOf(
            "--bind", "127.0.0.1:$SOCKS_PORT",
            "--http-proxy", "127.0.0.1:$HTTP_PORT",
            "--reconnect-secs", "1"
        )

        return when (mode.uppercase()) {
            "MASQUE_H2" ->
                listOf("--masque", "--h2", "-4") +
                    commonProxyArgs +
                    listOf(
                        "--scan", "balanced",
                        "--noize", "firewall",
                        "--quick-reconnect"
                    )

            "MASQUE_H3", "MASQUE" ->
                listOf("--masque", "-4") +
                    commonProxyArgs +
                    listOf(
                        "--scan", "balanced",
                        "--noize", "firewall",
                        "--quick-reconnect"
                    )

            "GOOL" ->
                listOf("--gool", "-4") +
                    commonProxyArgs +
                    listOf(
                        "--scan", "balanced",
                        "--noize", "balanced",
                        "--keepalive", "5",
                        "--quick-reconnect"
                    )

            else ->
                listOf("--wg", "-4") +
                    commonProxyArgs +
                    listOf(
                        "--scan", "balanced",
                        "--noize", "balanced",
                        "--keepalive", "5",
                        "--quick-reconnect"
                    )
        }
    }

    private fun modeLabel(mode: String): String =
        when (mode.uppercase()) {
            "MASQUE_H2" -> "MASQUE H2"
            "MASQUE_H3", "MASQUE" -> "MASQUE H3"
            "WG" -> "WG"
            "GOOL" -> "GOOL"
            else -> mode.ifBlank { "core" }
        }

    /**
     * Port-state probe that never connects to the SOCKS server.
     * This avoids generating app-created "early eof" entries.
     */
    private fun isPortInUse(port: Int): Boolean {
        val probe = ServerSocket()

        return try {
            probe.reuseAddress = false
            probe.bind(InetSocketAddress("127.0.0.1", port))
            false
        } catch (_: Throwable) {
            true
        } finally {
            runCatching { probe.close() }
        }
    }

    private fun hardExitCoreProcess(delayMs: Long) {
        Thread {
            Thread.sleep(delayMs)
            stopForeground(STOP_FOREGROUND_REMOVE)
            Process.killProcess(Process.myPid())
        }.start()
    }

    private fun setState(status: String, mode: String) {
        currentMode = mode
        LogStore.append(
            this,
            "STATE",
            "mode=$mode status=$status pid=${Process.myPid()}"
        )

        sendBroadcast(
            Intent(this, TunnelStateReceiver::class.java).apply {
                action = TunnelStateReceiver.ACTION_STATE
                putExtra(TunnelStateReceiver.EXTRA_STATUS, status)
                putExtra(TunnelStateReceiver.EXTRA_MODE, mode)
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Saman Tunnel Core",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
        }
    }

    private fun beginForeground(text: String) {
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("Saman Tunnel")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_saman_tunnel)
            .setColor(Color.rgb(68, 145, 255))
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
