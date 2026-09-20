package com.example.bedrockplayeranalyzer

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {
    private var window: WindowManager? = null
    private var view: TextView? = null

    override fun onCreate() {
        super.onCreate()
        window = getSystemService(WINDOW_SERVICE) as WindowManager
        view = TextView(this).apply {
            text = "ANALYZER\n🟢 live\nЖдите данных..."
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 25, 25, 25))
            setPadding(18, 12, 18, 12)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 80
        }
        window?.addView(view, params)
    }

    override fun onDestroy() {
        view?.let { runCatching { window?.removeView(it) } }
        view = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}