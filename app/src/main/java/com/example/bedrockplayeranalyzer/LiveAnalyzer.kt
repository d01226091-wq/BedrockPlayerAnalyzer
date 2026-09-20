package com.example.bedrockplayeranalyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LiveMarker(
    val name: String,
    val x: Float,
    val y: Float,
    val score: Int
)

object AnalyzerBus {
    @Volatile
    var markers: List<LiveMarker> = emptyList()
        private set

    @Volatile
    var analyzedFrames: Long = 0
        private set

    fun publish(newMarkers: List<LiveMarker>) {
        markers = newMarkers
        analyzedFrames++
    }
}

class LiveAnalyzer(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var previous: Bitmap? = null
    private var busy = false
    private var lastRun = 0L
    private val prefs = context.getSharedPreferences("players", Context.MODE_PRIVATE)

    fun analyze(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (busy || now - lastRun < 180L) {
            bitmap.recycle()
            return
        }
        busy = true
        lastRun = now

        val current = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val old = previous
        previous = current

        val image = InputImage.fromBitmap(current, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val markers = findPlayerLikeText(result, current, old)
                AnalyzerBus.publish(markers)
                saveObservations(markers)
            }
            .addOnCompleteListener {
                old?.recycle()
                current.recycle()
                busy = false
            }
    }

    private fun findPlayerLikeText(result: Text, current: Bitmap, old: Bitmap?): List<LiveMarker> {
        val out = mutableListOf<LiveMarker>()
        for (block in result.textBlocks) {
            val box = block.boundingBox ?: continue
            val raw = block.text.trim().replace("\n", " ")
            val name = raw.replace(Regex("[^A-Za-z0-9_А-Яа-яЁё-]"), "")
            if (!looksLikePlayerName(name, box, current.width, current.height)) continue

            val localMotion = motionAround(box, current, old)
            val score = behaviorScore(localMotion)
            out.add(
                LiveMarker(
                    name = name.take(24),
                    x = box.centerX().toFloat(),
                    y = box.bottom.toFloat(),
                    score = score
                )
            )
        }
        return out.distinctBy { it.name }.take(12)
    }

    private fun looksLikePlayerName(name: String, box: Rect, width: Int, height: Int): Boolean {
        if (name.length !in 3..20) return false
        if (box.width() < 12 || box.height() < 5) return false
        if (box.centerY() < height * 0.10f || box.centerY() > height * 0.88f) return false
        if (box.left < width * 0.02f || box.right > width * 0.98f) return false
        return name.any { it.isLetter() } && name.any { it.isDigit() || it == '_' || it.isLetter() }
    }

    private fun motionAround(box: Rect, current: Bitmap, old: Bitmap?): Double {
        if (old == null || old.width != current.width || old.height != current.height) return 0.0
        val padX = max(10, box.width())
        val padY = max(14, box.height() * 2)
        val l = max(0, box.left - padX)
        val t = max(0, box.top - padY)
        val r = min(current.width - 1, box.right + padX)
        val b = min(current.height - 1, box.bottom + padY)
        var total = 0L
        var count = 0L
        var y = t
        while (y < b) {
            var x = l
            while (x < r) {
                val a = current.getPixel(x, y)
                val o = old.getPixel(x, y)
                val ca = ((a shr 16) and 255) + ((a shr 8) and 255) + (a and 255)
                val co = ((o shr 16) and 255) + ((o shr 8) and 255) + (o and 255)
                total += abs(ca - co)
                count++
                x += 4
            }
            y += 4
        }
        return if (count == 0L) 0.0 else total.toDouble() / count / 765.0
    }

    private fun behaviorScore(motion: Double): Int {
        return when {
            motion > 0.42 -> 78
            motion > 0.28 -> 55
            motion > 0.15 -> 35
            else -> 15
        }
    }

    private fun saveObservations(markers: List<LiveMarker>) {
        if (markers.isEmpty()) return
        val arr = JSONArray(prefs.getString("records", "[]"))
        val map = linkedMapOf<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            map[o.optString("name").lowercase(Locale.ROOT)] = o
        }

        val now = System.currentTimeMillis()
        for (m in markers) {
            val key = m.name.lowercase(Locale.ROOT)
            val o = map[key] ?: JSONObject().apply {
                put("name", m.name)
                put("score", m.score)
                put("observations", 0)
                put("lastSeen", now)
                put("signals", JSONArray())
            }
            val oldScore = o.optInt("score", m.score)
            val oldObs = o.optInt("observations", 0)
            val newScore = ((oldScore * oldObs) + m.score) / (oldObs + 1)
            o.put("score", newScore)
            o.put("observations", oldObs + 1)
            o.put("lastSeen", now)
            val signals = o.optJSONArray("signals") ?: JSONArray()
            if (m.score >= 70) signals.put("резкие/аномальные движения")
            else if (m.score >= 35) signals.put("необычное движение")
            o.put("signals", signals)
            map[key] = o
        }

        val out = JSONArray()
        map.values.forEach { out.put(it) }
        prefs.edit().putString("records", out.toString()).apply()
    }

    fun close() {
        recognizer.close()
        previous?.recycle()
        previous = null
    }
}