package com.silentwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that runtime-registers a receiver for RINGER_MODE_CHANGED.
 * Manifest receivers can't catch this broadcast on Android 8+, so this is the only
 * way to refresh the widget in real time when the user changes ringer mode via
 * hardware volume buttons or the system volume panel.
 */
class RingerSyncService : Service() {

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.RINGER_MODE_CHANGED_ACTION,
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ->
                    RingerWidgetProvider.updateAll(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())
        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        registerReceiver(ringerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(ringerReceiver) }
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Widget sync",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Required to keep the widget in sync with hardware volume buttons. " +
                "This notification cannot be dismissed - Android requires foreground services to show one."
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setContentTitle("Silent Widget")
            .setContentText("Syncing widget with volume buttons")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ringer_sync"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RingerSyncService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingerSyncService::class.java))
        }
    }
}
