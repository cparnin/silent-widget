package com.silentwidget

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Handles widget button taps. Deliberately NOT exported — the widget's own
 * PendingIntents can always reach it, but other apps cannot forge a broadcast
 * to silence the phone or toggle DND. (The widget provider itself must stay
 * exported for the launcher, so click handling lives here instead.)
 */
class SetModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_MODE) return
        val mode = intent.getIntExtra(EXTRA_MODE, -1)
        if (mode !in 0..2) return

        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        try {
            when (mode) {
                AudioManager.RINGER_MODE_SILENT -> {
                    // Pixel ignores ringer-mode SILENT — use DND (alarms-only). This
                    // also drives mode_ringer to 0, so the volume rocker stays in sync.
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                }
                else -> {
                    // Clear DND first if it's on, then set the ringer mode.
                    if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                    audio.ringerMode = mode
                    // The async DND-off cascade can land after this write and clobber it
                    // back to the pre-DND mode. Re-assert shortly after to win the race.
                    Handler(Looper.getMainLooper()).postDelayed({
                        audio.ringerMode = mode
                        RingerWidgetProvider.updateAll(context)
                    }, 150)
                }
            }
        } catch (e: SecurityException) {
            // DND access not granted — send the user to the right Settings page.
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        RingerWidgetProvider.updateAll(context)
        // A user-initiated PendingIntent broadcast is exempt from the Android 12+
        // FGS-from-background restriction, so this safely (re)starts live sync.
        runCatching { RingerSyncService.start(context) }
    }

    companion object {
        const val ACTION_SET_MODE = "com.silentwidget.ACTION_SET_MODE"
        const val EXTRA_MODE = "mode"
    }
}
