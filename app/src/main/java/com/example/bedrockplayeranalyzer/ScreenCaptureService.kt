package com.example.bedrockplayeranalyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var analyzer: LiveAnalyzer? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("analyzer", "Analyzer", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(41, notification)
        }
        analyzer = LiveAnalyzer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        stopCapture()
        projection = manager.getMediaProjection(code, data)
        projection?.registerCallback(projectionCallback, null)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        reader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                analyzer?.analyze(imageToBitmap(image))
            } finally {
                image.close()
            }
        }, null)

        display = projection?.createVirtualDisplay(
            "BedrockPlayerAnalyzer",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            null
        )
        return START_NOT_STICKY
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)
        if (padded.width == image.width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun stopCapture() {
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            display?.release()
            display = null
            reader?.close()
            reader = null
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, "analyzer")
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentTitle("Bedrock Player Analyzer")
        .setContentText("Live-анализ экрана активен")
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        stopCapture()
        analyzer?.close()
        analyzer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}