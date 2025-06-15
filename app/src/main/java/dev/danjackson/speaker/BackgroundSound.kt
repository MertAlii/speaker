package dev.danjackson.speaker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BackgroundSound : Service() {

    private lateinit var monitor: Monitor

    override fun onCreate() {
        super.onCreate()
        monitor = Monitor.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shouldStart = intent?.getBooleanExtra("start", false) ?: false
        val showNotification = intent?.getBooleanExtra("show_notification", true) ?: true
        val hideNotification = intent?.getBooleanExtra("hide_notification", false) ?: false
        
        if (hideNotification) {
            // Bildirimleri gizle
            stopForeground(true)
            return START_NOT_STICKY
        }
        
        if (shouldStart) {
            if (showNotification) {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            monitor.mediaStart()
        } else {
            monitor.mediaStop()
            stopForeground(true)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.backgroundsound_description)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitor.mediaStop()
    }

    companion object {
        private const val CHANNEL_ID = "speaker_background_sound"
        private const val NOTIFICATION_ID = 1
    }
}
