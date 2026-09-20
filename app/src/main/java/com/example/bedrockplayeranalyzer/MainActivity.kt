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
            text = "BEDROCK PLAYER ANALYZER"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title, lp())

        val subtitle = TextView(this).apply {
            text = "Minecraft Bedrock 1.26.40.5 • LIVE ANALYTICS"
            textSize = 13f
            setTextColor(Color.LTGRAY)
        }
        root.addView(subtitle, lp())

        status = TextView(this).apply {
            text = "● Анализатор готов"
            textSize = 15f
            setTextColor(Color.rgb(80, 220, 100))
            setPadding(0, 18, 0, 18)
        }
        root.addView(status, lp())

        root.addView(Button(this).apply {
            text = "▶ ЗАПУСТИТЬ LIVE-АНАЛИЗ"
            setOnClickListener { requestCapture() }
        }, lp())

        root.addView(Button(this).apply {
            text = "▣ ВКЛЮЧИТЬ ОВЕРЛЕЙ"
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
            text = "СОХРАНИТЬ НАБЛЮДЕНИЕ"
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
            text = "ОЧИСТИТЬ ИСТОРИЮ"
            setOnClickListener {
                records.clear()
                saveRecords()
                render()
            }
        }, lp())

        root.addView(TextView(this).apply {
            text = "Важно: красный означает высокий поведенческий балл, а не доказанное наличие чита. Приложение видит только экран, автоматически запоминает наблюдения по никам и со временем усредняет результат."
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
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        records.values.sortedByDescending { it.lastSeen }.forEach { p ->
            val icon: String
            val label: String
            val color: Int
            when {
                p.score >= 70 -> {
                    icon = "🔴"
                    label = "СОФТ?"
                    color = Color.rgb(255, 90, 90)
                }
                p.score >= 35 -> {
                    icon = "🟡"
                    label = "ТРЕБУЕТ ПРОВЕРКИ"
                    color = Color.rgb(255, 210, 70)
                }
                else -> {
                    icon = "🟢"
                    label = "БЕЗ ЯВНЫХ ПРИЗНАКОВ"
                    color = Color.rgb(80, 220, 100)
                }
            }
            val row = TextView(this).apply {
                text = icon + " " + p.name + "\n" +
                    label + " • " + p.score + "% • наблюдений: " + p.observations + "\n" +
                    p.signals.distinct().take(4).joinToString(", ") + "\n" +
                    "Последний раз: " + fmt.format(Date(p.lastSeen))
                textSize = 15f
                setTextColor(color)
                setPadding(14, 14, 14, 14)
                setBackgroundColor(Color.rgb(30, 30, 30))
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8, 0, 0)
            list.addView(row, params)
        }
    }

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