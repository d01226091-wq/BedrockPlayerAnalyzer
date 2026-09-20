package com.example.bedrockplayeranalyzer

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayService : Service() {
    private var window: WindowManager? = null
    private var view: AnalyzerOverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false

    private val refresh = object : Runnable {
        override fun run() {
            if (!attached) return
            view?.markers = AnalyzerBus.markers
            view?.frames = AnalyzerBus.analyzedFrames
            view?.invalidate()
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Android запрещает TYPE_APPLICATION_OVERLAY без специального разрешения.
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        window = getSystemService(WINDOW_SERVICE) as? WindowManager
        if (window == null) {
            stopSelf()
            return
        }

        view = AnalyzerOverlayView().apply {
            markers = AnalyzerBus.markers
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            window?.addView(view, params)
            attached = true
            handler.post(refresh)
        } catch (_: SecurityException) {
            view = null
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            view = null
            stopSelf()
        } catch (_: RuntimeException) {
            view = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        if (attached) {
            view?.let { runCatching { window?.removeView(it) } }
        }
        attached = false
        view = null
        super.onDestroy()
    }

    private inner class AnalyzerOverlayView : View(this@OverlayService) {
        var markers: List<LiveMarker> = emptyList()
        var frames: Long = 0

        private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Компактный игровой HUD: приложение выглядит как модифицированный клиент,
            // но не вмешивается в код Minecraft.
            badge.color = Color.argb(190, 18, 18, 18)
            canvas.drawRoundRect(14f, 14f, 285f, 72f, 10f, 10f, badge)
            small.color = Color.WHITE
            small.textSize = 17f
            canvas.drawText("BPA MOD • LIVE", 28f, 38f, small)
            small.textSize = 13f
            small.color = Color.LTGRAY
            canvas.drawText("Игроков: " + markers.size + "  |  кадры: " + frames, 28f, 59f, small)

            if (markers.isEmpty()) {
                badge.color = Color.argb(170, 18, 18, 18)
                canvas.drawRoundRect(14f, 82f, 235f, 122f, 8f, 8f, badge)
                small.color = Color.WHITE
                small.textSize = 14f
                canvas.drawText("Сканирование игроков…", 27f, 107f, small)
                return
            }

            for (m in markers) {
                val color = when {
                    m.score >= 70 -> Color.rgb(235, 55, 55)
                    m.score >= 35 -> Color.rgb(245, 195, 45)
                    else -> Color.rgb(60, 210, 90)
                }

                // Minecraft уже рисует ник. Оверлей добавляет только цветной маркер над ним.
                badge.color = color
                canvas.drawCircle(m.x, m.y, 10f, badge)

                // Небольшой HUD-бейдж рядом с игроком.
                badge.color = Color.argb(185, 15, 15, 15)
                val left = (m.x + 15f).coerceAtMost(width - 105f)
                val top = (m.y - 34f).coerceAtLeast(8f)
                canvas.drawRoundRect(left, top, left + 92f, top + 30f, 7f, 7f, badge)
                small.color = Color.WHITE
                small.textSize = 16f
                val scoreText = m.score.toString() + "%"
                canvas.drawText("BPA  " + scoreText, left + 10f, top + 20f, small)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}