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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    // Анализ выполняется только когда на захваченном экране виден интерфейс Minecraft Bedrock.
    // Если экран не похож на игровой HUD, кадр игнорируется.
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var analyzer: LiveAnalyzer? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var stopped = false

    override fun onCreate() {
        super.onCreate()

        try {
            val channel = NotificationChannel(
                "analyzer",
                "Analyzer",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)

            val notification = notification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    41,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(41, notification)
            }

            captureThread = HandlerThread("BedrockCapture").also { it.start() }
            captureHandler = Handler(captureThread!!.looper)
            analyzer = LiveAnalyzer(this)
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stopped) return START_NOT_STICKY

        try {
            val code = intent?.getIntExtra("resultCode", 0) ?: 0
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra("data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra<Intent>("data")
            }

            if (code != android.app.Activity.RESULT_OK || data == null) {
                stopSelf()
                return START_NOT_STICKY
            }

            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            stopCapture()

            projection = manager.getMediaProjection(code, data)
                ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }

            projection?.registerCallback(projectionCallback, captureHandler)

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(metrics)

            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val maxWidth = 1280
            val captureWidth = minOf(screenWidth, maxWidth)
            val captureHeight =
                (screenHeight.toDouble() * captureWidth / screenWidth)
                    .toInt()
                    .coerceAtLeast(360)

            reader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                android.graphics.PixelFormat.RGBA_8888,
                2
            )

            reader?.setOnImageAvailableListener({ source ->
                val image = try {
                    source.acquireLatestImage()
                } catch (_: Throwable) {
                    null
                } ?: return@setOnImageAvailableListener

                try {
                    val bitmap = imageToBitmap(image)
                    if (!looksLikeMinecraft(bitmap)) {
                        bitmap.recycle()
                        return@setOnImageAvailableListener
                    }
                    analyzer?.analyze(
                        bitmap,
                        screenWidth.toFloat() / captureWidth.toFloat(),
                        screenHeight.toFloat() / captureHeight.toFloat()
                    )
                } catch (_: Throwable) {
                    // A bad frame must never crash the analyzer service.
                } finally {
                    try {
                        image.close()
                    } catch (_: Throwable) {
                    }
                }
            }, captureHandler)

            display = projection?.createVirtualDisplay(
                "BedrockPlayerAnalyzer",
                captureWidth,
                captureHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface,
                null,
                captureHandler
            )

            if (display == null) {
                stopSelf()
                return START_NOT_STICKY
            }

            return START_NOT_STICKY
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun looksLikeMinecraft(bitmap: Bitmap): Boolean {
        // Быстрый визуальный фильтр: проверяем несколько участков экрана на типичные
        // элементы Bedrock HUD (полосы/панели в нижней части и игровой центр).
        // Это не идентификация игры по приватным данным, а фильтр изображения.
        if (bitmap.width < 500 || bitmap.height < 300) return false
        val w = bitmap.width
        val h = bitmap.height
        var nonDark = 0
        var samples = 0
        val yStart = (h * 0.72f).toInt().coerceAtLeast(0)
        var y = yStart
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                if (r + g + b > 90) nonDark++
                samples++
                x += 24
            }
            y += 24
        }
        return samples > 0 && nonDark.toFloat() / samples > 0.08f
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes.firstOrNull()
            ?: throw IllegalStateException("No image plane")

        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        if (pixelStride <= 0 || rowStride <= 0) {
            throw IllegalStateException("Invalid image stride")
        }

        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride

        val padded = Bitmap.createBitmap(
            paddedWidth,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)

        if (paddedWidth == image.width) return padded

        val cropped = Bitmap.createBitmap(
            padded,
            0,
            0,
            image.width,
            image.height
        )
        padded.recycle()
        return cropped
    }

    private fun stopCapture(stopProjection: Boolean = true) {
        try {
            display?.release()
        } catch (_: Throwable) {
        }
        display = null

        try {
            reader?.setOnImageAvailableListener(null, null)
            reader?.close()
        } catch (_: Throwable) {
        }
        reader = null

        try {
            projection?.unregisterCallback(projectionCallback)
        } catch (_: Throwable) {
        }

        if (stopProjection) {
            try {
                projection?.stop()
            } catch (_: Throwable) {
            }
        }
        projection = null
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture(stopProjection = false)
        }
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, "analyzer")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Bedrock Player Analyzer")
            .setContentText("Live-анализ экрана активен")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        stopped = true
        stopCapture()

        try {
            analyzer?.close()
        } catch (_: Throwable) {
        }
        analyzer = null

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
