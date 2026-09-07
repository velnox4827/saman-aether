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
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AetherService : Service() {
    companion object {
        const val ACTION_START = "com.saman.tunnel.START"
        const val ACTION_STOP = "com.saman.tunnel.STOP"
        const val EXTRA_MODE = "mode"
        const val PREFS = "saman_tunnel"
        const val KEY_STATUS = "status"
        const val KEY_MODE = "mode"
        const val KEY_LAST_MODE = "last_mode"
        const val KEY_PHASE = "phase"

        private const val CHANNEL_ID = "saman_tunnel_core"
        private const val NOTIFICATION_ID = 1819
        private const val SOCKS_PORT = 1819
        private const val HTTP_PORT = 1820
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val jobLock = Any()
    private val terminating = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var jobId: Long = 0L
    @Volatile private var generation: Long = 0L
    @Volatile private var currentMode: String = ""
    @Volatile private var currentPhase: TunnelPhase = TunnelPhase.STOPPED
    @Volatile private var currentStatus: String = "Stopped"

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
        var restartPolicy = START_NOT_STICKY
        when (intent?.action) {
            ACTION_STOP -> {
                if (!terminating.get()) beginForeground("Stopping tunnel…")
                stopCore()
            }

            ACTION_START -> {
                val mode =
                    intent.getStringExtra(EXTRA_MODE)
                        ?.ifBlank { "WG" }
                        ?: "WG"

                if (jobId != 0L || currentPhase.isBusy || terminating.get()) {
                    LogStore.append(
                        this,
                        "SERVICE",
                        "Ignoring duplicate start mode=$mode jobId=$jobId"
                    )
                    setState(currentStatus, currentMode)
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
                restartPolicy = START_NOT_STICKY
            }

            else -> stopSelf(startId)
        }

        return restartPolicy
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Explicit stops reach here after cleanup. Unexpected destruction uses
        // the same cancellation path and cannot leave detached native listeners.
        if (!terminating.get()) finishCore(null)
        super.onDestroy()
    }

    private fun requestStart(mode: String) {
        val myGeneration = ++generation
        setState("Starting…", mode)
        updateNotification("Starting ${modeLabel(mode)}…")

        executor.execute {
            try {
                if (!waitForLocalPorts(myGeneration)) {
                    if (generation != myGeneration) return@execute
                    fail(
                        "Port 1819/1820 is still in use after restart wait.",
                        mode
                    )
                    return@execute
                }

                if (generation != myGeneration) return@execute
                launchCore(mode, myGeneration)
            } catch (t: Throwable) {
                if (generation != myGeneration || terminating.get()) return@execute
                LogStore.append(this, "EXCEPTION", t.stackTraceToString())
                fail(t.message ?: t.javaClass.simpleName, mode)
            }
        }
    }

    private fun waitForLocalPorts(myGeneration: Long): Boolean {
        var waited = false

        // 8 * 250 ms = 2 seconds. This covers the short hand-off window
        // after the isolated core process is killed, without changing
        // tunnel scan/reconnect settings.
        for (attempt in 0 until 8) {
            if (generation != myGeneration) return false

            val socksBusy = isPortInUse(SOCKS_PORT)
            val httpBusy = isPortInUse(HTTP_PORT)

            if (!socksBusy && !httpBusy) {
                if (waited) {
                    LogStore.append(
                        this,
                        "PORT",
                        "Local proxy ports became reusable after ${attempt * 250}ms"
                    )
                }
                return true
            }

            if (!waited) {
                waited = true
                LogStore.append(
                    this,
                    "PORT",
                    "Waiting for local port hand-off: SOCKS5=$socksBusy HTTP=$httpBusy"
                )
            }

            Thread.sleep(250)
        }

        LogStore.append(
            this,
            "PORT",
            "Local ports still busy after 2s: SOCKS5=${isPortInUse(SOCKS_PORT)} HTTP=${isPortInUse(HTTP_PORT)}"
        )
        return false
    }

    private fun launchCore(mode: String, myGeneration: Long) {
        val modeArguments = argumentsFor(mode)
        val args = JSONArray(modeArguments).toString()
        LogStore.append(this, "CORE", "Starting mode=$mode argumentCount=${modeArguments.size}")

        val started = synchronized(jobLock) {
            if (generation != myGeneration || terminating.get()) return
            JSONObject(NativeBridge.startCore(args, filesDir.absolutePath)).also {
                if (it.optBoolean("ok")) jobId = it.optLong("job", 0L)
            }
        }
        LogStore.append(this, "NATIVE", "startCore ok=${started.optBoolean("ok")}")

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

        if (generation != myGeneration || terminating.get()) return
        LogStore.append(
            this,
            "CORE",
            "Core job started id=$id pid=${Process.myPid()}"
        )

        setState("Connecting…", mode)
        updateNotification("Connecting ${modeLabel(mode)}…")

        var lastPhase = ""

        // 480 * 250 ms = the original 120-second ceiling.
        for (i in 0 until 480) {
            if (generation != myGeneration || jobId != id) return

            val rawPoll = synchronized(jobLock) {
                if (generation != myGeneration || jobId != id) return
                NativeBridge.pollJob(id)
            }
            val polled = JSONObject(rawPoll)

            if (polled.optString("state") == "done") {
                val result = polled.optJSONObject("result")
                LogStore.append(this, "NATIVE", "Job ended before proxy ready")
                if (claimJob(id) == 0L) return
                releaseJob(id)
                fail(
                    result?.optString("error")
                        ?: "Core stopped before local proxy became ready",
                    mode
                )
                return
            }

            if (i % 4 == 0) {
                val phase =
                    LogStore.connectionPhase(
                        this,
                        mode
                    )

                if (
                    !phase.isNullOrBlank() &&
                    phase != lastPhase
                ) {
                    lastPhase = phase

                    setState(
                        "Connecting — $phase",
                        mode
                    )

                    updateNotification(
                        "${modeLabel(mode)} — $phase"
                    )
                }
            }

            if (ProxyHealth.probeSocks5("127.0.0.1", SOCKS_PORT, 750)) {
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
                val rawPoll = synchronized(jobLock) {
                if (generation != myGeneration || jobId != id) return
                NativeBridge.pollJob(id)
            }
                val polled = JSONObject(rawPoll)

                if (polled.optString("state") == "done") {
                    LogStore.append(this, "NATIVE", "Job completed")
                    val result = polled.optJSONObject("result")
                    val error = result?.optString("error")
                    if (claimJob(id) == 0L) return
                    releaseJob(id)

                    if (!error.isNullOrBlank()) {
                        fail(error, mode)
                    } else {
                        finishCore(null)
                    }
                    return
                }

                if (ProxyHealth.probeSocks5("127.0.0.1", SOCKS_PORT, 750)) {
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
            } catch (t: InterruptedException) {
                Thread.currentThread().interrupt()

                if (generation != myGeneration || jobId != id) {
                    LogStore.append(
                        this,
                        "CORE",
                        "Monitor interrupted during normal shutdown"
                    )
                    return
                }

                LogStore.append(
                    this,
                    "EXCEPTION",
                    "monitor interrupted unexpectedly"
                )
                fail("Monitoring interrupted", mode)
                return
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

        if (terminating.get()) return
        setState(status, mode)

        updateNotification(
            if (httpReady) {
                "${modeLabel(mode)} Proxy connected — SOCKS5 + HTTP"
            } else {
                "${modeLabel(mode)} Proxy connected — SOCKS5"
            }
        )
    }

    private fun stopCore() = finishCore(null)

    private fun fail(message: String, mode: String) {
        if (terminating.get()) return
        LogStore.append(this, "ERROR", "mode=$mode message=$message")
        finishCore(message)
    }

    private fun finishCore(error: String?) {
        if (!terminating.compareAndSet(false, true)) return
        val mode = currentMode
        val id = synchronized(jobLock) {
            generation++
            claimJob()
        }
        setState(error?.let { "Error: $it" } ?: "Stopping…", mode)
        runCatching {
            startService(Intent(this, SamanVpnService::class.java).apply {
                action = SamanVpnService.ACTION_STOP
            })
        }
        Thread({
            var jobDone = id == 0L
            if (id != 0L) runCatching {
                val reply = JSONObject(NativeBridge.cancelJob(id))
                LogStore.append(this, "NATIVE", "cancel accepted=${reply.optBoolean("ok")}")
            }
            // Cancellation is a request, not an acknowledgement. Wait for both
            // the job and its SOCKS/HTTP listeners before announcing Stopped.
            val deadline = android.os.SystemClock.elapsedRealtime() + 4_000L
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (!jobDone) jobDone = runCatching {
                    JSONObject(NativeBridge.pollJob(id)).optString("state") == "done"
                }.getOrDefault(false)
                if (jobDone && !isPortInUse(SOCKS_PORT) && !isPortInUse(HTTP_PORT)) break
                Thread.sleep(100)
            }
            if (id != 0L && jobDone) releaseJob(id)
            val needsRecycle = !jobDone || isPortInUse(SOCKS_PORT) || isPortInUse(HTTP_PORT)
            LogStore.append(this, "CORE_STOP", "jobDone=$jobDone portsClosed=${!needsRecycle}")
            executor.shutdownNow()
            mainHandler.post {
                // Only this dedicated process can be recycled. Never kill the UI.
                val isolated = runCatching {
                    java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000') ==
                        "$packageName:aether_core"
                }.getOrDefault(false)
                if (needsRecycle && !isolated) {
                    setState("Error: Core cleanup incomplete", mode)
                } else if (error == null) {
                    setState("Stopped", "")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                if (isolated) {
                    // Aether also owns a process-wide Rust runtime. End this
                    // dedicated process so no detached client or runtime thread
                    // survives Stop, even when both listener ports are closed.
                    LogStore.append(this, "CORE_STOP", "Recycling isolated core; cleanupTimeout=$needsRecycle")
                    Process.killProcess(Process.myPid())
                }
            }
        }, "saman-core-stop").start()
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
     * Port-state probe that never connects to SOCKS/HTTP.
     *
     * SO_REUSEADDR prevents recently closed TCP state from being mistaken
     * for another live local proxy. A genuinely active listener still
     * blocks the bind because SO_REUSEPORT is not used.
     */
    private fun isPortInUse(port: Int): Boolean {
        val probe = ServerSocket()

        return try {
            probe.reuseAddress = true
            probe.bind(InetSocketAddress("127.0.0.1", port))
            false
        } catch (_: Throwable) {
            true
        } finally {
            runCatching { probe.close() }
        }
    }

    private fun releaseJob(id: Long) {
        runCatching { NativeBridge.freeJob(id) }
            .onFailure {
                LogStore.append(this, "NATIVE", "freeJob failed: ${it.javaClass.simpleName}")
            }
    }

    private fun claimJob(expectedId: Long? = null): Long =
        synchronized(jobLock) {
            val id = jobId
            if (id == 0L || (expectedId != null && id != expectedId)) {
                0L
            } else {
                jobId = 0L
                id
            }
        }

    private fun setState(status: String, mode: String) {
        val phase = TunnelPhase.fromStatus(status)
        if (terminating.get() && phase !in listOf(TunnelPhase.STOPPING, TunnelPhase.STOPPED, TunnelPhase.FAILED)) return
        currentMode = mode
        currentStatus = status
        currentPhase = TunnelPhase.fromStatus(status)
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
                putExtra(TunnelStateReceiver.EXTRA_PHASE, currentPhase.name)
            }
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        LogStore.append(this, "SERVICE", "Foreground-service timeout type=$fgsType")
        stopCore()
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
        if (terminating.get()) return
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

        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherService::class.java).apply { action = ACTION_STOP },
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
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
            .build()
    }
}
