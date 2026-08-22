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

    @Volatile
    private var jobId: Long = 0L

    @Volatile
    private var generation: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCore()
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: "WG"
                beginForeground("Starting $mode…")
                startCore(mode)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        generation++
        if (jobId != 0L) {
            runCatching { NativeBridge.cancelJob(jobId) }
            runCatching { NativeBridge.freeJob(jobId) }
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
            runCatching { NativeBridge.cancelJob(previous) }
            runCatching { NativeBridge.freeJob(previous) }
            jobId = 0L
        }

        executor.execute {
            try {
                val args = JSONArray(argumentsFor(mode)).toString()
                val started = JSONObject(NativeBridge.startCore(args, filesDir.absolutePath))
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
                setState("Connecting…", mode)
                updateNotification("Connecting $mode…")

                for (i in 0 until 120) {
                    if (generation != myGeneration || jobId != id) return@execute

                    val polled = JSONObject(NativeBridge.pollJob(id))
                    if (polled.optString("state") == "done") {
                        val result = polled.optJSONObject("result")
                        val error = result?.optString("error")
                        fail(error ?: "Core stopped before SOCKS5 became ready", mode)
                        return@execute
                    }

                    if (isSocksReady()) {
                        setState("Connected — SOCKS5 127.0.0.1:1819", mode)
                        updateNotification("$mode connected — 127.0.0.1:1819")
                        monitor(id, mode, myGeneration)
                        return@execute
                    }

                    Thread.sleep(1000)
                }

                fail("SOCKS5 did not become ready", mode)
            } catch (t: Throwable) {
                fail(t.message ?: t.javaClass.simpleName, mode)
            }
        }
    }

    private fun monitor(id: Long, mode: String, myGeneration: Long) {
        while (generation == myGeneration && jobId == id) {
            try {
                val polled = JSONObject(NativeBridge.pollJob(id))
                if (polled.optString("state") == "done") {
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

                if (!isSocksReady()) {
                    setState("Reconnecting…", mode)
                    updateNotification("$mode reconnecting…")
                } else {
                    setState("Connected — SOCKS5 127.0.0.1:1819", mode)
                }

                Thread.sleep(2000)
            } catch (t: Throwable) {
                fail(t.message ?: "Monitoring failed", mode)
                return
            }
        }
    }

    private fun stopCore() {
        val id = jobId
        generation++
        setState("Stopping…", prefs().getString(KEY_MODE, "") ?: "")

        executor.execute {
            if (id != 0L) {
                runCatching { NativeBridge.cancelJob(id) }
                Thread.sleep(350)
                runCatching { NativeBridge.freeJob(id) }
            }
            jobId = 0L
            setState("Stopped", "")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fail(message: String, mode: String) {
        setState("Error: $message", mode)
        updateNotification("Error — open Saman Tunnel")
    }

    private fun argumentsFor(mode: String): List<String> = when (mode.uppercase()) {
        "MASQUE" -> listOf(
            "--masque", "-4",
            "--bind", "127.0.0.1:1819",
            "--scan", "balanced",
            "--noize", "firewall",
            "--quick-reconnect"
        )

        "GOOL" -> listOf(
            "--gool", "-4",
            "--bind", "127.0.0.1:1819",
            "--scan", "balanced",
            "--noize", "balanced",
            "--keepalive", "5",
            "--quick-reconnect"
        )

        else -> listOf(
            "--wg", "-4",
            "--bind", "127.0.0.1:1819",
            "--scan", "balanced",
            "--noize", "balanced",
            "--keepalive", "5",
            "--quick-reconnect"
        )
    }

    private fun isSocksReady(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 1819), 500)
        }
        true
    } catch (_: Throwable) {
        false
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun setState(status: String, mode: String) {
        prefs().edit()
            .putString(KEY_STATUS, status)
            .putString(KEY_MODE, mode)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Saman Tunnel")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
