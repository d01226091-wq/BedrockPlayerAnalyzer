package com.example.bedrockplayeranalyzer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.Typeface
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class PlayerRecord(
    val name: String,
    var score: Int,
    var observations: Int,
    var lastSeen: Long,
    var signals: MutableList<String> = mutableListOf()
)

class MainActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) {
            loadRecords()
            render()
        }
    }

    private val records = linkedMapOf<String, PlayerRecord>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private val prefs by lazy { getSharedPreferences("players", Context.MODE_PRIVATE) }
    private val captureRequest = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadRecords()
        buildUi()
        render()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "BPA MOD CLIENT"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title, lp())

        val subtitle = TextView(this).apply {
            text = "BEDROCK • MOD HUD • LIVE ANALYTICS"
            textSize = 13f
            setTextColor(Color.LTGRAY)
        }
        root.addView(subtitle, lp())

        status = TextView(this).apply {
            text = "● MOD CLIENT ГОТОВ"
            textSize = 15f
            setTextColor(Color.rgb(80, 220, 100))
            setPadding(0, 18, 0, 18)
        }
        root.addView(status, lp())

        stats = TextView(this).apply {
            text = "Игроков: 0   •   Средний балл: 0%"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(25, 27, 32))
        }
        root.addView(stats, lp(0, 10))

        root.addView(Button(this).apply {
            text = "▶ ВКЛЮЧИТЬ MOD HUD"
            setOnClickListener { requestCapture() }
        }, lp())

        root.addView(Button(this).apply {
            text = "▣ ПОКАЗАТЬ HUD ПОВЕРХ ИГРЫ"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + packageName)
                        )
                    )
                    Toast.makeText(
                        this@MainActivity,
                        "Разреши показ поверх других приложений и вернись сюда",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    try {
                        startService(Intent(this@MainActivity, OverlayService::class.java))
                        status.text = "● ОВЕРЛЕЙ ЗАПУЩЕН"
                        status.setTextColor(Color.rgb(80, 220, 100))
                        Toast.makeText(
                            this@MainActivity,
                            "Цветные индикаторы включены",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: SecurityException) {
                        status.text = "● Нет разрешения на оверлей"
                        status.setTextColor(Color.RED)
                        Toast.makeText(
                            this@MainActivity,
                            "Android не дал разрешение на оверлей",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: RuntimeException) {
                        status.text = "● Не удалось запустить оверлей"
                        status.setTextColor(Color.RED)
                        Toast.makeText(
                            this@MainActivity,
                            "Оверлей не запустился. Проверь разрешение «поверх других приложений».",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }, lp())

        val name = EditText(this).apply {
            hint = "Ник игрока"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
        }
        root.addView(name, lp())

        val signals = arrayOf(
            "Необычная скорость",
            "Подозрительное наведение",
            "Аномальные удары",
            "Резкие движения",
            "Другое"
        )
        val checks = signals.map { label ->
            CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
            }
        }
        checks.forEach { root.addView(it, lp()) }

        val score = SeekBar(this).apply { max = 100; progress = 0 }
        root.addView(score, lp())

        root.addView(Button(this).apply {
            text = "ДОБАВИТЬ В MOD-ПРОФИЛЬ"
            setOnClickListener {
                val player = name.text.toString().trim()
                if (player.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Введите ник", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val selected = checks.filter { it.isChecked }.map { it.text.toString() }
                val old = records[player]
                if (old == null) {
                    records[player] = PlayerRecord(
                        player,
                        score.progress,
                        1,
                        System.currentTimeMillis(),
                        selected.toMutableList()
                    )
                } else {
                    old.score = ((old.score * old.observations) + score.progress) /
                        (old.observations + 1)
                    old.observations++
                    old.lastSeen = System.currentTimeMillis()
                    old.signals.addAll(selected)
                }
                saveRecords()
                name.text.clear()
                checks.forEach { it.isChecked = false }
                score.progress = 0
                render()
            }
        }, lp())

        root.addView(Button(this).apply {
            text = "ОЧИСТИТЬ MOD-ПРОФИЛИ"
            setOnClickListener {
                records.clear()
                saveRecords()
                render()
            }
        }, lp())

        root.addView(TextView(this).apply {
            text = "MOD HUD работает поверх Minecraft: показывает цветные маркеры и поведенческий балл по тому, что видно на экране. Это отдельный Android-оверлей, а не изменённый APK Minecraft."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 14, 0, 14)
        }, lp())

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, lp())
        setContentView(scroll)
    }

    private fun requestCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Activity result kept simple for AndroidIDE compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(intent)
            status.text = "● LIVE-АНАЛИЗ ЗАПУЩЕН • экран доступен анализатору"
            status.setTextColor(Color.rgb(80, 220, 100))
        }
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()

        val total = records.size
        val avg = if (total == 0) 0 else records.values.sumOf { it.score } / total
        stats.text = "Игроков: $total   •   Средний балл: $avg%"

        if (records.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Пока игроков нет\n\nЗапусти LIVE-анализ или добавь наблюдение ниже."
                textSize = 14f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(28), dp(20), dp(28))
                setBackgroundColor(Color.rgb(25, 27, 32))
            }, lp())
            return
        }

        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        records.values.sortedByDescending { it.lastSeen }.forEach { p ->
            val (state, color) = when {
                p.score >= 70 -> "ВЫСОКИЙ БАЛЛ" to Color.rgb(240, 75, 75)
                p.score >= 35 -> "ТРЕБУЕТ ПРОВЕРКИ" to Color.rgb(245, 195, 55)
                else -> "НИЗКИЙ БАЛЛ" to Color.rgb(70, 215, 105)
            }

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(13), dp(15), dp(13))
                setBackgroundColor(Color.rgb(25, 27, 32))
            }
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(label("●", 20f, color), LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT))
            header.addView(label(p.name, 17f, Color.WHITE, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(label(p.score.toString() + "%", 18f, color, true))
            item.addView(header, lp())
            item.addView(label(state, 12f, color, true), lp(4, 0))
            item.addView(label("Наблюдений: " + p.observations + "  •  " + fmt.format(Date(p.lastSeen)), 12f, Color.LTGRAY), lp(5, 0))

            val signals = p.signals.distinct().take(3)
            if (signals.isNotEmpty()) {
                item.addView(label("Сигналы: " + signals.joinToString(" • "), 12f, Color.LTGRAY), lp(5, 0))
            }

            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, dp(7), 0, 0)
            list.addView(item, params)
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun lp(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(0, dp(top), 0, dp(bottom))
        return p
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun saveRecords() {
        val arr = JSONArray()
        records.values.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("score", p.score)
                put("observations", p.observations)
                put("lastSeen", p.lastSeen)
                put("signals", JSONArray(p.signals))
            })
        }
        prefs.edit().putString("records", arr.toString()).apply()
    }

    private fun loadRecords() {
        val raw = prefs.getString("records", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val signals = mutableListOf<String>()
                val s = o.optJSONArray("signals")
                if (s != null) {
                    for (j in 0 until s.length()) signals.add(s.getString(j))
                }
                val p = PlayerRecord(
                    o.getString("name"),
                    o.getInt("score"),
                    o.getInt("observations"),
                    o.getLong("lastSeen"),
                    signals
                )
                records[p.name] = p
            }
        }
    }

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}