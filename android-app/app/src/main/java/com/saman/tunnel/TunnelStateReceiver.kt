package com.saman.tunnel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TunnelStateReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STATE =
            "com.saman.tunnel.STATE_CHANGED"

        const val EXTRA_STATUS = "status"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PHASE = "phase"
        const val ACTION_VPN_STATE = "com.saman.tunnel.VPN_STATE_CHANGED"
        const val EXTRA_RUNNING = "running"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // This receiver runs in the UI process, the only writer of preferences.
        // SharedPreferences does not synchronize cached data across processes.
        if (intent.action == ACTION_VPN_STATE) {
            val status = intent.getStringExtra(EXTRA_STATUS) ?: "Stopped"
            context.getSharedPreferences(SamanVpnService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(SamanVpnService.KEY_RUNNING, intent.getBooleanExtra(EXTRA_RUNNING, false))
                .putString(SamanVpnService.KEY_STATUS, status)
                .commit()
            LogStore.append(context, "VPN-STATE-RX", status)
            SamanTunnelWidget.updateAll(context)
            return
        }
        if (intent.action != ACTION_STATE) return

        val status =
            intent.getStringExtra(EXTRA_STATUS)
                ?: "Stopped"

        val mode =
            intent.getStringExtra(EXTRA_MODE)
                ?: ""

        val phase = intent.getStringExtra(EXTRA_PHASE)
            ?: TunnelPhase.fromStatus(status).name

        context.getSharedPreferences(
            AetherService.PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(AetherService.KEY_STATUS, status)
            .putString(AetherService.KEY_MODE, mode)
            .putString(AetherService.KEY_PHASE, phase)
            .commit()

        LogStore.append(
            context,
            "STATE-RX",
            "mode=$mode status=$status"
        )

        SamanTunnelWidget.updateAll(context)
    }
}
