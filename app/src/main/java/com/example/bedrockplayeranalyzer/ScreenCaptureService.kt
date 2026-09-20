package com.example.bedrockplayeranalyzer

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var analyzer: LiveAnalyzer? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val channel = NotificationChannel("analyzer", "Analyzer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val n = NotificationCompat.Builder(this, "analyzer")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Bedrock Player Analyzer")
                .setContentText("Live-анализ экрана активен")
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(41, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(41, n)
            analyzer = LiveAnalyzer(this)
        } catch (e: Throwable) {
            Log.e("BedrockAnalyzer", "capture service init failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val code = intent?.getIntExtra("resultCode", 0) ?: 0
            val data = intent?.getParcelableExtra<Intent>("data")
            if (code != Activity.RESULT_OK || data == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            stopCapture()
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            if (projection == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            projection!!.registerCallback(projectionCallback, null)

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

            val w = metrics.widthPixels
            val h = metrics.heightPixels
            reader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
            reader!!.setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (e: Throwable) {
                    Log.e("BedrockAnalyzer", "acquire image failed", e)
                    null
                } ?: return@setOnImageAvailableListener
                try {
                    analyzer?.analyze(imageToBitmap(image))
                } catch (e: Throwable) {
                    Log.e("BedrockAnalyzer", "frame analysis failed", e)
                } finally {
                    runCatching { image.close() }
                }
            }, null)

            display = projection!!.createVirtualDisplay(
                "BedrockPlayerAnalyzer", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, null
            )
            if (display == null) stopSelf()
        } catch (e: Throwable) {
            Log.e("BedrockAnalyzer", "capture start failed", e)
            stopCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val p = image.planes[0]
        val rowPadding = p.rowStride - p.pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / p.pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(p.buffer)
        if (padded.width == image.width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun stopCapture() {
        runCatching { display?.release() }
        display = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            runCatching { display?.release() }
            display = null
            runCatching { reader?.close() }
            reader = null
        }
    }

    override fun onDestroy() {
        stopCapture()
        analyzer?.close()
        analyzer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
