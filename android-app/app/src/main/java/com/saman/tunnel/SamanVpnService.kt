package com.saman.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SamanVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.saman.tunnel.VPN_START"
        const val ACTION_STOP = "com.saman.tunnel.VPN_STOP"

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

        private const val TUN_IPV4 = "198.18.0.1"
        private const val TUN_PREFIX = 30
        private const val TUN_MTU = 1400

        private const val SOCKS_HOST = "127.0.0.1"
        private const val SOCKS_PORT = 1819
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val starting = AtomicBoolean(false)

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null

    @Volatile
    private var vpnRunning = false

    override fun onBind(intent: Intent?): IBinder? =
        super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                executor.execute { stopTunnel("Stopped") }
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                ensureForeground("Preparing VPN…")

                if (!vpnRunning && starting.compareAndSet(false, true)) {
                    executor.execute {
                        try {
                            startTunnel()
                        } finally {
                            starting.set(false)
                        }
                    }
                }

                return START_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        LogStore.append(this, "VPN_STOP", "Android revoked VPN permission")
        executor.execute { stopTunnel("Permission revoked") }
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { HevBridge.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
        vpnRunning = false
        saveState(false, "Stopped")
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startTunnel() {
        saveState(false, "Waiting for Aether SOCKS5")
        updateNotification("Waiting for Aether…")
        LogStore.append(this, "VPN_START", "Waiting for SOCKS5 127.0.0.1:1819")

        var socksReady = false

        for (attempt in 0 until 120) {
            if (Thread.currentThread().isInterrupted) return

            if (ProxyHealth.probeSocks5(SOCKS_HOST, SOCKS_PORT, 600)) {
                socksReady = true
                break
            }

            Thread.sleep(250)
        }

        if (!socksReady) {
            fail("Aether SOCKS5 did not become ready")
            return
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val routingMode =
            prefs.getString(KEY_ROUTING_MODE, ROUTING_ALL) ?: ROUTING_ALL
        val selectedApps =
            prefs.getStringSet(KEY_SELECTED_APPS, emptySet())
                ?.toSet()
                ?: emptySet()

        val builder = Builder()
            .setSession("Saman Tunnel")
            .setBlocking(false)
            .setMtu(TUN_MTU)
            .addAddress(TUN_IPV4, TUN_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        try {
            applyAppRouting(builder, routingMode, selectedApps)
        } catch (t: Throwable) {
            fail("App routing failed: ${t.message ?: t.javaClass.simpleName}")
            return
        }

        LogStore.append(
            this,
            "APP_ROUTING",
            "mode=$routingMode selectedCount=${selectedApps.size}"
        )

        val established = runCatching { builder.establish() }.getOrNull()

        if (established == null) {
            fail("Android could not establish the VPN interface")
            return
        }

        tunInterface = established

        LogStore.append(
            this,
            "TUN_CREATED",
            "ipv4=$TUN_IPV4/$TUN_PREFIX mtu=$TUN_MTU"
        )

        val config = File(cacheDir, "hev-saman-vpn.yml")
        config.writeText(
            """
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
              address: '$SOCKS_HOST'
              udp: 'udp'
            """.trimIndent()
        )

        if (!HevBridge.isLoaded()) {
            val nativeError = HevBridge.getLoadError().ifBlank { "unknown JNI load error" }
            LogStore.append(this, "ERROR", "HEV JNI unavailable: $nativeError")
            runCatching { established.close() }
            tunInterface = null
            fail("HEV JNI unavailable")
            return
        }

        LogStore.append(this, "HEV_START", "Starting HEV on Android TUN")

        val started = runCatching {
            HevBridge.start(config.absolutePath, established.fd)
        }.getOrElse {
            LogStore.append(this, "ERROR", "HEV start exception: ${it.javaClass.simpleName}")
            false
        }

        if (!started) {
            val nativeError = HevBridge.getLoadError().ifBlank { "native start returned false" }
            LogStore.append(this, "ERROR", "HEV start failed: $nativeError")
            runCatching { established.close() }
            tunInterface = null
            fail("HEV could not start")
            return
        }

        Thread.sleep(300)

        if (!HevBridge.isRunning()) {
            LogStore.append(this, "ERROR", "HEV worker exited immediately")
            runCatching { established.close() }
            tunInterface = null
            fail("HEV worker exited")
            return
        }

        vpnRunning = true
        saveState(true, "VPN connected")
        updateNotification("VPN connected")
        LogStore.append(this, "VPN_START", "VPN connected through HEV → SOCKS5 :1819")
    }

    private fun applyAppRouting(
        builder: Builder,
        mode: String,
        selectedApps: Set<String>
    ) {
        when (mode) {
            ROUTING_ONLY -> {
                val apps = selectedApps.filter { it != packageName }

                require(apps.isNotEmpty()) {
                    "No apps selected for Only selected mode"
                }

                apps.forEach { pkg ->
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                        LogStore.append(this, "APP_ROUTING", "Skipping uninstalled selected app")
                    }
                }
            }

            ROUTING_BYPASS -> {
                val apps = selectedApps + packageName

                apps.forEach { pkg ->
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                        LogStore.append(this, "APP_ROUTING", "Skipping unavailable bypass app")
                    }
                }
            }

            else -> {
                builder.addDisallowedApplication(packageName)
            }
        }
    }

    private fun stopTunnel(status: String) {
        LogStore.append(this, "VPN_STOP", "Stopping HEV and TUN")

        if (HevBridge.isRunning()) {
            runCatching { HevBridge.stop() }
            LogStore.append(this, "HEV_STOP", "HEV stopped")
        }

        runCatching { tunInterface?.close() }
        tunInterface = null
        vpnRunning = false

        saveState(false, status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        LogStore.append(this, "ERROR", "VPN: $message")
        saveState(false, "Error: $message")
        updateNotification("VPN error — open Saman Tunnel")

        runCatching { HevBridge.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
        vpnRunning = false

        Thread.sleep(150)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveState(running: Boolean, status: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, running)
            .putString(KEY_STATUS, status)
            .apply()
    }

    private fun ensureForeground(text: String) {
        createNotificationChannel()
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun updateNotification(text: String) {
        createNotificationChannel()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1821,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setSmallIcon(R.mipmap.saman_app_icon_v120)
            .setContentTitle("Saman Tunnel VPN")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Saman Tunnel VPN",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
