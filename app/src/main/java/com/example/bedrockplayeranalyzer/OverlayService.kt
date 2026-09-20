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
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayService : Service() {
    private var window: WindowManager? = null
    private var view: AnalyzerOverlayView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            view?.markers = AnalyzerBus.markers
            view?.frames = AnalyzerBus.analyzedFrames
            view?.invalidate()
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        window = getSystemService(WINDOW_SERVICE) as WindowManager
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
        window?.addView(view, params)
        handler.post(refresh)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        view?.let { runCatching { window?.removeView(it) } }
        view = null
        super.onDestroy()
    }

    private inner class AnalyzerOverlayView : View(this@OverlayService) {
        var markers: List<LiveMarker> = emptyList()
        var frames: Long = 0

        private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            if (markers.isEmpty()) {
                small.color = Color.WHITE
                canvas.drawText("ANALYZER • ждёт игроков", 20f, 45f, small)
                return
            }

            for (m in markers) {
                val color = when {
                    m.score >= 70 -> Color.rgb(235, 55, 55)
                    m.score >= 35 -> Color.rgb(245, 195, 45)
                    else -> Color.rgb(60, 210, 90)
                }
                circle.color = color
                canvas.drawCircle(m.x, m.y, 10f, circle)

                text.color = Color.WHITE
                canvas.drawText(m.name, m.x + 16f, m.y + 8f, text)

                small.color = color
                val label = when {
                    m.score >= 70 -> "СОФТ?"
                    m.score >= 35 -> "ПОДОЗРИТЕЛЬНО"
                    else -> "НИЗКИЙ РИСК"
                }
                canvas.drawText("$label • ${m.score}%", m.x + 16f, m.y + 32f, small)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}