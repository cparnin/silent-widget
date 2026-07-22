package com.silentwidget

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.RemoteViews

/**
 * Renders the widget and keeps it current. Button taps are handled by the
 * non-exported [SetModeReceiver]; this class stays exported only because the
 * launcher requires it for APPWIDGET_UPDATE.
 */
class RingerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        widgetIds.forEach { renderWidget(context, manager, it) }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed - drop the sync service (and its notification).
        RingerSyncService.stop(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            // Cheap re-sync on unlock: catches ringer changes made while the
            // sync service wasn't running (e.g. after a force-stop).
            updateAll(context)
        }
    }

    companion object {

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RingerWidgetProvider::class.java)
            )
            ids.forEach { renderWidget(context, manager, it) }
        }

        /**
         * The "effective" mode for display. On Pixel, ringer-mode SILENT doesn't
         * stick - silence is achieved via DND. So if DND is anything stricter
         * than ALL, show Silent regardless of the underlying ringer mode.
         */
        private fun currentDisplayMode(context: Context): Int {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                return AudioManager.RINGER_MODE_SILENT
            }
            return context.getSystemService(AudioManager::class.java).ringerMode
        }

        private fun renderWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val current = currentDisplayMode(context)
            val views = RemoteViews(context.packageName, R.layout.ringer_widget)

            val buttons = listOf(
                R.id.btn_ring to AudioManager.RINGER_MODE_NORMAL,
                R.id.btn_vibrate to AudioManager.RINGER_MODE_VIBRATE,
                R.id.btn_silent to AudioManager.RINGER_MODE_SILENT,
            )
            buttons.forEach { (buttonId, mode) ->
                views.setInt(
                    buttonId, "setBackgroundResource",
                    if (current == mode) R.drawable.btn_active else 0
                )
                views.setOnClickPendingIntent(buttonId, modeClick(context, mode))
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun modeClick(context: Context, mode: Int): PendingIntent {
            val intent = Intent(context, SetModeReceiver::class.java).apply {
                action = SetModeReceiver.ACTION_SET_MODE
                putExtra(SetModeReceiver.EXTRA_MODE, mode)
            }
            return PendingIntent.getBroadcast(
                context, mode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
