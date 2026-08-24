package com.saman.tunnel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class SamanTunnelWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WIDGET_TOGGLE) {
            toggle(context)
        }
    }

    companion object {
        private const val ACTION_WIDGET_TOGGLE =
            "com.saman.tunnel.WIDGET_TOGGLE"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component =
                ComponentName(context, SamanTunnelWidget::class.java)

            manager.getAppWidgetIds(component).forEach {
                updateOne(context, manager, it)
            }
        }

        private fun toggle(context: Context) {
            val prefs = context.getSharedPreferences(
                AetherService.PREFS,
                Context.MODE_PRIVATE
            )

            val status = prefs.getString(
                AetherService.KEY_STATUS,
                "Stopped"
            ).orEmpty()

            val active =
                status.startsWith("Connected", true) ||
                status.startsWith("Connection unstable", true) ||
                status.startsWith("Starting", true) ||
                status.startsWith("Connecting", true) ||
                status.startsWith("Switching", true) ||
                status.startsWith("Stopping", true)

            val serviceIntent =
                Intent(context, AetherService::class.java)

            if (active) {
                prefs.edit()
                    .putString(AetherService.KEY_STATUS, "Stopping…")
                    .apply()

                serviceIntent.action = AetherService.ACTION_STOP
            } else {
                val lastMode = prefs.getString(
                    AetherService.KEY_LAST_MODE,
                    "WG"
                ).orEmpty().ifBlank { "WG" }

                prefs.edit()
                    .putString(AetherService.KEY_STATUS, "Starting…")
                    .putString(AetherService.KEY_MODE, lastMode)
                    .putString(AetherService.KEY_LAST_MODE, lastMode)
                    .apply()

                serviceIntent.action = AetherService.ACTION_START
                serviceIntent.putExtra(
                    AetherService.EXTRA_MODE,
                    lastMode
                )
            }

            updateAll(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val prefs = context.getSharedPreferences(
                AetherService.PREFS,
                Context.MODE_PRIVATE
            )

            val status = prefs.getString(
                AetherService.KEY_STATUS,
                "Stopped"
            ).orEmpty()

            val currentMode = prefs.getString(
                AetherService.KEY_MODE,
                ""
            ).orEmpty()

            val lastMode = prefs.getString(
                AetherService.KEY_LAST_MODE,
                "WG"
            ).orEmpty().ifBlank { "WG" }

            val mode =
                if (currentMode.isNotBlank()) currentMode else lastMode

            val displayMode = when (mode.uppercase()) {
                "MASQUE_H2" -> "H2"
                "MASQUE_H3", "MASQUE" -> "H3"
                "GOOL" -> "GOOL"
                else -> "WG"
            }

            val state = when {
                status.startsWith("Connected", true) ->
                    State("● $displayMode", R.color.widget_state_on)

                status.startsWith("Connection unstable", true) ->
                    State("▲ $displayMode", R.color.widget_state_warn)

                status.startsWith("Error", true) ->
                    State("! $displayMode", R.color.widget_state_error)

                status.startsWith("Starting", true) ||
                status.startsWith("Connecting", true) ||
                status.startsWith("Switching", true) ||
                status.startsWith("Stopping", true) ->
                    State("… $displayMode", R.color.widget_state_working)

                else ->
                    State("○ $displayMode", R.color.widget_state_off)
            }

            val views = RemoteViews(
                context.packageName,
                R.layout.widget_quick_connect
            )

            views.setTextViewText(
                R.id.widget_state,
                state.label
            )

            views.setTextColor(
                R.id.widget_state,
                context.getColor(state.colorRes)
            )

            views.setOnClickPendingIntent(
                R.id.widget_root,
                togglePendingIntent(context)
            )

            manager.updateAppWidget(widgetId, views)
        }

        private fun togglePendingIntent(
            context: Context
        ): PendingIntent {
            val intent = Intent(
                context,
                SamanTunnelWidget::class.java
            ).apply {
                action = ACTION_WIDGET_TOGGLE
            }

            return PendingIntent.getBroadcast(
                context,
                1300,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        }

        private data class State(
            val label: String,
            val colorRes: Int
        )
    }
}
