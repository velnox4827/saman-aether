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
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATE) return

        val status =
            intent.getStringExtra(EXTRA_STATUS)
                ?: "Stopped"

        val mode =
            intent.getStringExtra(EXTRA_MODE)
                ?: ""

        context.getSharedPreferences(
            AetherService.PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(AetherService.KEY_STATUS, status)
            .putString(AetherService.KEY_MODE, mode)
            .commit()

        LogStore.append(
            context,
            "STATE-RX",
            "mode=$mode status=$status"
        )

        SamanTunnelWidget.updateAll(context)
    }
}
