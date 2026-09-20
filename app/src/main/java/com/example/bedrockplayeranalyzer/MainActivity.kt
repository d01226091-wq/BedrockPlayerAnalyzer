package com.example.bedrockplayeranalyzer

import android.app.Activity
import android.app.AlertDialog
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
import android.graphics.drawable.GradientDrawable
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
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(22), dp(18), dp(24))
            setBackgroundColor(Color.rgb(35, 37, 42))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "BEDROCK ANALYZER"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(dp(2).toFloat(), 0f, dp(2).toFloat(), Color.BLACK)
        }, lp(0, 4))

        root.addView(TextView(this).apply {
            text = "PLAYER ANALYTICS"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(150, 160, 175))
        }, lp(0, 22))

        status = TextView(this).apply {
            text = "● ГОТОВ"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(85, 220, 120))
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status, lp(0, 10))

        root.addView(menuButton("▶  ИГРАТЬ", Color.rgb(76, 126, 72)) {
            showPlayMenu()
        }, lp(0, 10))

        root.addView(menuButton("◉  АНАЛИЗ ЭКРАНА", Color.rgb(72, 74, 80)) {
            requestCapture()
        }, lp(0, 10))

        root.addView(menuButton("⚙  НАСТРОЙКИ", Color.rgb(72, 74, 80)) {
            showSettings()
        }, lp(0, 10))

        root.addView(menuButton("▣  HUD ПОВЕРХ ИГРЫ", Color.rgb(65, 75, 90)) {
            startOverlay()
        }, lp(0, 18))

        stats = TextView(this).apply {
            text = "ИГРОКОВ  0    •    СРЕДНИЙ БАЛЛ  0%"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 33, 40))
                cornerRadius = dp(6).toFloat()
            }
        }
        root.addView(stats, lp(0, 18))

        root.addView(TextView(this).apply {
            text = "ПОСЛЕДНИЕ НАБЛЮДЕНИЯ"
            textSize = 12f
            setTextColor(Color.rgb(145, 155, 170))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }, lp())

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(list, lp())

        root.addView(menuButton("＋  ДОБАВИТЬ НАБЛЮДЕНИЕ", Color.rgb(65, 75, 90)) {
            showManualPanel(root)
        }, lp(0, 10))

        root.addView(menuButton("×  ОЧИСТИТЬ ДАННЫЕ", Color.rgb(75, 55, 60)) {
            records.clear()
            saveRecords()
            render()
        }, lp(0, 10))

        root.addView(TextView(this).apply {
            text = "Уникальный интерфейс анализатора. Анализ работает только по тому, что видно на экране."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(125, 135, 150))
            setPadding(dp(8), dp(22), dp(8), dp(8))
        }, lp())

        setContentView(scroll)
    }

    private fun menuButton(title: String, color: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            minHeight = dp(54)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(7).toFloat()
                setStroke(dp(1), Color.argb(80, 255, 255, 255))
            }
            setOnClickListener { action() }
        }

    private fun showPlayMenu() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setBackgroundColor(Color.rgb(30, 32, 36))
        }

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun tab(title: String, action: () -> Unit): Button =
            Button(this).apply {
                text = title
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(Color.rgb(70, 72, 78))
                    setStroke(dp(1), Color.rgb(25, 26, 29))
                }
                setOnClickListener { action() }
            }

        tabs.addView(tab("МИРЫ") { createMyWorld() },
            LinearLayout.LayoutParams(0, dp(54), 1f))
        tabs.addView(tab("ИГРА С ДРУГОМ") { connectToFriend() },
            LinearLayout.LayoutParams(0, dp(54), 1f))
        tabs.addView(tab("СЕРВЕРЫ") { showServerDialog() },
            LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(tabs)

        root.addView(TextView(this).apply {
            text = "МИРЫ"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(12))
        }, lp())

        val worlds = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        val create = Button(this).apply {
            text = "＋\nСОЗДАТЬ МИР"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            gravity = Gravity.CENTER
            minWidth = dp(210)
            minHeight = dp(100)
            background = GradientDrawable().apply {
                setColor(Color.rgb(75, 125, 72))
                setStroke(dp(2), Color.rgb(28, 45, 27))
                cornerRadius = dp(3).toFloat()
            }
            setOnClickListener { createMyWorld() }
        }
        worlds.addView(create, LinearLayout.LayoutParams(dp(220), dp(105)))

        worlds.addView(TextView(this).apply {
            text = "Создай новый мир в Minecraft Bedrock"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(10), 0, dp(4))
        })

        root.addView(worlds, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        AlertDialog.Builder(this)
            .setTitle("ИГРАТЬ")
            .setView(root)
            .setNegativeButton("НАЗАД", null)
            .show()
    }

    private fun createMyWorld() {
        showWorldStudio()
    }

    private fun showWorldStudio() {
        val name = EditText(this).apply {
            hint = "Название мира"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText("Новый мир")
        }
        val seed = EditText(this).apply {
            hint = "Seed (необязательно)"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val modes = arrayOf("Выживание", "Творческий")
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modes)
        }
        val preview = TextView(this).apply {
            text = buildWorldPreview(seed.text.toString())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(210, 220, 210))
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(Color.rgb(22, 28, 22))
        }
        seed.setOnEditorActionListener { _, _, _ ->
            preview.text = buildWorldPreview(seed.text.toString())
            false
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
            addView(label("МИР В ПРИЛОЖЕНИИ", 12f, Color.rgb(100, 220, 120), true))
            addView(name, lp(8, 0))
            addView(seed, lp(8, 0))
            addView(mode, lp(8, 0))
            addView(preview, lp(12, 0))
        }

        AlertDialog.Builder(this)
            .setTitle("СОЗДАНИЕ МИРА")
            .setView(box)
            .setPositiveButton("СОЗДАТЬ") { _, _ ->
                val worldName = name.text.toString().trim().ifEmpty { "Новый мир" }
                val worldSeed = seed.text.toString().trim().ifEmpty { System.currentTimeMillis().toString() }
                saveLocalWorld(worldName, worldSeed, mode.selectedItemPosition)
                showWorldPreview(worldName, worldSeed, mode.selectedItemPosition)
            }
            .setNegativeButton("НАЗАД", null)
            .show()
    }

    private fun saveLocalWorld(name: String, seed: String, mode: Int) {
        prefs.edit()
            .putString("local_world_name", name)
            .putString("local_world_seed", seed)
            .putInt("local_world_mode", mode)
            .apply()
    }

    private fun showWorldPreview(name: String, seed: String, mode: Int) {
        val info = TextView(this).apply {
            text = "МИР: " + name + "\nРЕЖИМ: " + (if (mode == 0) "ВЫЖИВАНИЕ" else "ТВОРЧЕСКИЙ") + "\nSEED: " + seed + "\n\n" + buildWorldPreview(seed)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setBackgroundColor(Color.rgb(24, 27, 30))
        }
        AlertDialog.Builder(this)
            .setTitle("МИР СОЗДАН")
            .setView(info)
            .setPositiveButton("ГОТОВО", null)
            .show()
    }

    private fun buildWorldPreview(seedText: String): String {
        val seed = seedText.hashCode().toLong()
        val chars = charArrayOf('·', '·', '·', '▒', '▓', '▓', '█')
        val out = StringBuilder()
        for (y in 0 until 11) {
            for (x in 0 until 19) {
                val n = ((seed + x * 31L + y * 97L + x * y * 13L) xor (seed ushr ((x + y) % 7))).toInt()
                out.append(chars[kotlin.math.abs(n) % chars.size])
            }
            out.append('\\n')
        }
        return out.toString()
    }

    private fun connectToFriend() {
        openMinecraftPlay()
    }

    private fun openMinecraftPlay() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("minecraft://openServersTab"))
        runCatching { startActivity(intent) }.onFailure { openMinecraft() }
    }

    private fun openMinecraft() {
        val intent = packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
        if (intent != null) startActivity(intent)
        else {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("minecraft://"))
            runCatching { startActivity(fallback) }.onFailure {
                Toast.makeText(this, "Minecraft Bedrock не найден. Установи официальное приложение Minecraft.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showServerDialog() {
        val host = EditText(this).apply {
            hint = "Адрес сервера"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val port = EditText(this).apply {
            hint = "Порт (например 19132)"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(host)
            addView(port, lp(8, 0))
        }
        AlertDialog.Builder(this)
            .setTitle("ПОДКЛЮЧЕНИЕ К СЕРВЕРУ")
            .setView(box)
            .setPositiveButton("ПОДКЛЮЧИТЬСЯ") { _, _ ->
                val address = host.text.toString().trim()
                val p = port.text.toString().trim().ifEmpty { "19132" }
                if (address.isEmpty()) {
                    Toast.makeText(this, "Введите адрес сервера", Toast.LENGTH_SHORT).show()
                } else {
                    val uri = Uri.parse("minecraft://connect?serverUrl=" + Uri.encode(address) + "&serverPort=" + Uri.encode(p))
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }.onFailure {
                        val addUri = Uri.parse("minecraft://?addExternalServer=" + Uri.encode("Bedrock Server") + "|" + Uri.encode(address + ":" + p))
                        startActivity(Intent(Intent.ACTION_VIEW, addUri))
                    }
                }
            }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            Toast.makeText(this, "Разреши показ поверх других приложений", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            startService(Intent(this, OverlayService::class.java))
            status.text = "● HUD ЗАПУЩЕН"
        }.onFailure {
            status.text = "● ОШИБКА HUD"
            Toast.makeText(this, "Не удалось запустить HUD", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettings() {
        val controls = arrayOf(
            "Обычное управление Minecraft",
            "Управление касанием",
            "Автоанализ экрана",
            "Показывать цветные маркеры",
            "Сохранять историю игроков"
        )
        val checked = booleanArrayOf(true, true, true, true, true)
        AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setMultiChoiceItems(controls, checked) { _, _, _ -> }
            .setPositiveButton("Готово", null)
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showManualPanel(root: LinearLayout) {
        val name = EditText(this).apply {
            hint = "Ник игрока"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val score = SeekBar(this).apply { max = 100 }
        val checks = arrayOf(
            CheckBox(this).apply { text = "Необычная скорость" },
            CheckBox(this).apply { text = "Подозрительное наведение" },
            CheckBox(this).apply { text = "Аномальные удары" }
        )
        AlertDialog.Builder(this)
            .setTitle("Новое наблюдение")
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(name)
                checks.forEach { addView(it) }
                addView(score)
            })
            .setPositiveButton("Сохранить") { _, _ ->
                val player = name.text.toString().trim()
                if (player.isNotEmpty()) {
                    records[player] = PlayerRecord(
                        player, score.progress, 1, System.currentTimeMillis(),
                        checks.filter { it.isChecked }.map { it.text.toString() }.toMutableList()
                    )
                    saveRecords()
                    render()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun blockButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(70, 70, 70))
                setStroke(dp(2), Color.rgb(35, 35, 35))
                cornerRadius = 2f
            }
            setOnClickListener { action() }
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