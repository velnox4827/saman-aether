package com.saman.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SamanVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.saman.tunnel.VPN_START"
        const val ACTION_STOP = "com.saman.tunnel.VPN_STOP"
        const val ACTION_RECONFIGURE = "com.saman.tunnel.VPN_RECONFIGURE"
        const val ACTION_QUERY = "com.saman.tunnel.VPN_QUERY"
        const val EXTRA_ROUTING_MODE = "com.saman.tunnel.extra.ROUTING_MODE"
        const val EXTRA_SELECTED_APPS = "com.saman.tunnel.extra.SELECTED_APPS"
        const val PREFS = "saman_vpn"
        const val KEY_CONNECTION_MODE = "connection_mode"
        const val KEY_ROUTING_MODE = "routing_mode"
        const val KEY_SELECTED_APPS = "selected_apps"
        const val KEY_RUNNING = "vpn_running"
        const val KEY_STATUS = "vpn_status"
        const val CONNECTION_PROXY = "PROXY"
        const val CONNECTION_VPN = "VPN"
        const val ROUTING_ALL = "ALL"
        const val ROUTING_ONLY = "ONLY"
        const val ROUTING_BYPASS = "BYPASS"
        private const val CHANNEL_ID = "saman_vpn"
        private const val NOTIFICATION_ID = 1821
        private const val TUN_MTU = 1400
        private const val SOCKS_PORT = 1819
    }

    private data class Request(val mode: String, val routing: String, val apps: Set<String>)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ending = AtomicBoolean(false)
    private val cleanupComplete = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private var monitor: ScheduledFuture<*>? = null
    private var tunInterface: ParcelFileDescriptor? = null
    @Volatile private var starting = false
    @Volatile private var vpnRunning = false
    @Volatile private var lastStatus = "Stopped"
    @Volatile private var currentMode = ""
    @Volatile private var coreBinder: IBinder? = null
    private var coreBindingRequested = false

    private val coreConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!ending.get()) {
                coreBinder = service
                LogStore.append(this@SamanVpnService, "VPN_CORE", "Core process connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = coreDisconnected()

        override fun onBindingDied(name: ComponentName) = coreDisconnected()

        override fun onNullBinding(name: ComponentName) {
            coreBinder = null
            if (!ending.get()) requestStop("Error: Aether core is not running")
        }
    }

    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        LogStore.append(this, "VPN_SERVICE", "created pid=${android.os.Process.myPid()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> requestStop("Stopped")
            ACTION_QUERY -> {
                saveState(vpnRunning, lastStatus)
                if (!starting && !vpnRunning) stopSelf(startId)
            }
            ACTION_START, ACTION_RECONFIGURE -> {
                if (ending.get()) return START_NOT_STICKY
                if (intent.action == ACTION_START && (starting || vpnRunning)) {
                    // Repeated commands must not regress a connected notification.
                    ensureForeground(lastStatus)
                    saveState(vpnRunning, lastStatus)
                    return START_NOT_STICKY
                }
                val request = Request(
                    intent.getStringExtra(AetherService.EXTRA_MODE).orEmpty(),
                    intent.getStringExtra(EXTRA_ROUTING_MODE) ?: ROUTING_ALL,
                    intent.getStringArrayListExtra(EXTRA_SELECTED_APPS)?.toSet() ?: emptySet()
                )
                val ticket = generation.incrementAndGet()
                starting = true
                vpnRunning = false
                currentMode = request.mode
                saveState(false, "Preparing VPN")
                ensureForeground("${ConnectionStatus.modeLabel(currentMode)} VPN preparing…")
                if (!bindToCore()) {
                    requestStop("Error: Aether core is unavailable")
                    return START_NOT_STICKY
                }
                executor.execute {
                    try {
                        cleanupNative()
                        if (isCurrent(ticket)) startTunnel(request, ticket)
                    } catch (t: Throwable) {
                        if (isCurrent(ticket)) {
                            LogStore.append(this, "VPN_ERROR", t.stackTraceToString())
                            requestStop("Error: VPN ${t.message ?: t.javaClass.simpleName}", stopCore = true)
                        }
                    } finally {
                        if (ticket == generation.get()) starting = false
                    }
                }
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun isCurrent(ticket: Long) = !ending.get() && ticket == generation.get()

    private fun bindToCore(): Boolean {
        if (coreBindingRequested) return true
        coreBindingRequested = true
        return runCatching {
            // Observe the already-started core without recreating it after Stop.
            bindService(Intent(this, AetherService::class.java), coreConnection, Context.BIND_IMPORTANT)
        }.onFailure {
            LogStore.append(this, "VPN_CORE", "Binding failed: ${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    private fun coreDisconnected() {
        coreBinder = null
        LogStore.append(this, "VPN_CORE", "Core process disconnected; closing VPN")
        // Explicit core Stop and process death can race with ACTION_STOP.
        // Preserve a normal Stop; the core's own error, if any, stays in its state.
        if (!ending.get()) requestStop("Stopped")
    }

    private fun unbindCore() {
        if (coreBindingRequested) {
            coreBindingRequested = false
            runCatching { unbindService(coreConnection) }
        }
        coreBinder = null
    }

    override fun onRevoke() {
        LogStore.append(this, "VPN_STOP", "Android revoked VPN permission")
        requestStop("Permission revoked", stopCore = true)
    }

    override fun onDestroy() {
        // Native join/close stays on the worker, never Android's main thread.
        if (!ending.get()) requestStop(lastStatus.takeIf {
            it.startsWith("Error") || it.startsWith("Permission")
        } ?: "Stopped")
        unbindCore()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        requestStop("Error: VPN service timed out", stopCore = true)
    }

    private fun startTunnel(request: Request, ticket: Long) {
        saveState(false, "Waiting for Aether SOCKS5")
        updateNotification("${ConnectionStatus.modeLabel(request.mode)} VPN waiting for proxy…")
        val deadline = android.os.SystemClock.elapsedRealtime() + 30_000L
        var ready = false
        while (isCurrent(ticket) && android.os.SystemClock.elapsedRealtime() < deadline) {
            if (coreBinder?.isBinderAlive == true &&
                ProxyHealth.probeSocks5("127.0.0.1", SOCKS_PORT, 600)) {
                ready = true
                break
            }
            Thread.sleep(150)
        }
        if (!isCurrent(ticket)) return
        check(ready) { "Aether SOCKS5 did not become ready" }

        val builder = Builder()
            .setSession("Saman Tunnel ${ConnectionStatus.modeLabel(request.mode)}")
            .setBlocking(false)
            .setMtu(TUN_MTU)
            .addAddress("198.18.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        applyAppRouting(builder, request.routing, request.apps)
        if (!isCurrent(ticket)) return
        val established = checkNotNull(builder.establish()) { "Android did not establish the VPN" }
        tunInterface = established
        if (!isCurrent(ticket)) return
        LogStore.append(this, "TUN_CREATED", "mode=${request.mode} routing=${request.routing} mtu=$TUN_MTU")
        val config = File(cacheDir, "hev-saman-vpn.yml")
        config.writeText("""
            misc:
              task-stack-size: 24576
              tcp-read-write-timeout: 300000
              udp-read-write-timeout: 60000
              log-level: warn
            tunnel:
              mtu: $TUN_MTU
              icmp: 'reply'
            socks5:
              port: $SOCKS_PORT
              address: '127.0.0.1'
              udp: 'udp'
        """.trimIndent())
        check(HevBridge.isLoaded()) { "HEV unavailable: ${HevBridge.getLoadError()}" }
        if (!isCurrent(ticket)) return
        check(HevBridge.start(config.absolutePath, established.fd)) {
            "HEV start failed: ${HevBridge.getLoadError()}"
        }
        Thread.sleep(300)
        if (!isCurrent(ticket)) return
        check(HevBridge.isRunning()) { "HEV worker exited" }
        vpnRunning = true
        val text = "${ConnectionStatus.modeLabel(request.mode)} VPN connected"
        saveState(true, text)
        updateNotification(text)
        LogStore.append(this, "VPN_START", text)

        val health = VpnHealthPolicy()
        monitor = executor.scheduleWithFixedDelay({
            if (isCurrent(ticket)) {
                val coreAlive = coreBinder?.isBinderAlive == true
                val workerAlive = HevBridge.isRunning()
                val proxyReady = coreAlive && workerAlive &&
                    ProxyHealth.probeSocks5("127.0.0.1", SOCKS_PORT, 600)
                if (!isCurrent(ticket)) return@scheduleWithFixedDelay
                // Aether intentionally closes/reopens SOCKS5 while selecting a
                // new endpoint. Keep TUN/HEV alive while the core process lives.
                when (health.observe(coreAlive, workerAlive, proxyReady)) {
                    VpnHealthPolicy.State.CORE_STOPPED -> requestStop("Stopped")
                    VpnHealthPolicy.State.WORKER_STOPPED ->
                        requestStop("Error: VPN worker stopped", stopCore = true)
                    VpnHealthPolicy.State.RECONNECTING -> {
                        val reconnecting = "Connecting — ${ConnectionStatus.modeLabel(request.mode)} VPN reconnecting"
                        if (lastStatus != reconnecting) {
                            saveState(true, reconnecting)
                            updateNotification("${ConnectionStatus.modeLabel(request.mode)} VPN reconnecting…")
                            LogStore.append(this, "VPN_RECONNECT", "Proxy unavailable; retaining TUN and live core")
                        }
                    }
                    VpnHealthPolicy.State.CONNECTED -> {
                        if (lastStatus != text) {
                            saveState(true, text)
                            updateNotification(text)
                            LogStore.append(this, "VPN_RECONNECT", "Proxy recovered; VPN resumed without closing TUN")
                        }
                    }
                }
            }
        }, 2, 2, TimeUnit.SECONDS)
    }

    private fun applyAppRouting(builder: Builder, mode: String, selectedApps: Set<String>) {
        when (mode) {
            ROUTING_ONLY -> {
                var added = 0
                selectedApps.filter { it != packageName }.forEach { pkg ->
                    try {
                        builder.addAllowedApplication(pkg)
                        added++
                    } catch (_: PackageManager.NameNotFoundException) {
                        LogStore.append(this, "APP_ROUTING", "Skipping uninstalled selected app")
                    }
                }
                // An empty allow list means all apps to Android, including the
                // core; do not accidentally build a recursive VPN route.
                require(added > 0) { "No installed apps selected for Only selected mode" }
            }
            ROUTING_BYPASS -> (selectedApps + packageName).forEach { pkg ->
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    LogStore.append(this, "APP_ROUTING", "Skipping uninstalled bypass app")
                }
            }
            else -> builder.addDisallowedApplication(packageName)
        }
    }

    private fun cleanupNative() {
        monitor?.cancel(false)
        monitor = null
        // Join even if isRunning is false: a finished worker can still be joinable.
        runCatching { HevBridge.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
        vpnRunning = false
    }

    private fun requestStop(status: String, stopCore: Boolean = false) {
        if (!ending.compareAndSet(false, true)) return
        generation.incrementAndGet() // Cancel startup before the queued cleanup.
        starting = false
        // A native join must not hold the Android VPN indefinitely. This process
        // owns only the bridge; recycling it closes its TUN file descriptor.
        mainHandler.postDelayed({
            if (!cleanupComplete.get()) {
                val isolated = runCatching {
                    File("/proc/self/cmdline").readText().trimEnd('\u0000') == "$packageName:vpn"
                }.getOrDefault(false)
                if (isolated) {
                    saveState(false, status)
                    LogStore.append(this, "VPN_STOP", "Recycling isolated VPN after cleanup timeout")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }, 4_000L)
        if (stopCore) runCatching {
            startService(Intent(this, AetherService::class.java).apply { action = AetherService.ACTION_STOP })
        }
        executor.execute {
            cleanupNative()
            cleanupComplete.set(true)
            saveState(false, status)
            LogStore.append(this, "VPN_STOP", "HEV and TUN closed: $status")
            mainHandler.post {
                unbindCore()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        executor.shutdown()
    }

    private fun saveState(running: Boolean, status: String) {
        lastStatus = status
        sendBroadcast(Intent(this, TunnelStateReceiver::class.java).apply {
            action = TunnelStateReceiver.ACTION_VPN_STATE
            putExtra(TunnelStateReceiver.EXTRA_STATUS, status)
            putExtra(TunnelStateReceiver.EXTRA_RUNNING, running)
        })
    }

    private fun ensureForeground(text: String) {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, buildNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, buildNotification(text))
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 1821, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1822, Intent(this, AetherService::class.java).apply {
            action = AetherService.ACTION_STOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.ic_stat_saman_tunnel)
            .setContentTitle("Saman Tunnel VPN").setContentText(text)
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Saman Tunnel VPN", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
