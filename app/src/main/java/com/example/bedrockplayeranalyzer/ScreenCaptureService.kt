package com.example.bedrockplayeranalyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("analyzer", "Analyzer", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(41, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data != null) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection?.stop()
            projection = manager.getMediaProjection(code, data)
        }
        return START_STICKY
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, "analyzer")
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentTitle("Bedrock Player Analyzer")
        .setContentText("Live-анализ экрана активен")
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}