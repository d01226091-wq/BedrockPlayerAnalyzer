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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "BedrockAnalyzer").apply {
                isDaemon = true
            }
        }

    private var previous: Bitmap? = null
    private var busy = false
    private var closed = false
    private var lastRun = 0L
    private val prefs = context.getSharedPreferences("players", Context.MODE_PRIVATE)

    fun analyze(bitmap: Bitmap, scaleX: Float = 1f, scaleY: Float = 1f) {
        if (closed) {
            bitmap.recycleSafely()
            return
        }

        val now = System.currentTimeMillis()
        if (busy || now - lastRun < 250L) {
            bitmap.recycleSafely()
            return
        }

        busy = true
        lastRun = now

        executor.execute {
            if (closed) {
                bitmap.recycleSafely()
                busy = false
                return@execute
            }

            val current = try {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: Throwable) {
                bitmap.recycleSafely()
                busy = false
                return@execute
            } finally {
                bitmap.recycleSafely()
            }

            val old = previous
            previous = current

            val image = InputImage.fromBitmap(current, 0)

            recognizer.process(image)
                .addOnSuccessListener(executor) { result ->
                    if (closed) return@addOnSuccessListener

                    try {
                        val markers = findPlayerLikeText(
                            result,
                            current,
                            old,
                            scaleX,
                            scaleY
                        )
                        AnalyzerBus.publish(markers)
                        saveObservations(markers)
                    } catch (_: Throwable) {
                        // Ignore a malformed OCR result.
                    }
                }
                .addOnCompleteListener(executor) {
                    old.recycleSafely()
                    current.recycleSafely()
                    busy = false
                }
        }
    }

    private fun findPlayerLikeText(
        result: Text,
        current: Bitmap,
        old: Bitmap?,
        scaleX: Float,
        scaleY: Float
    ): List<LiveMarker> {
        val out = mutableListOf<LiveMarker>()

        for (block in result.textBlocks) {
            val box = block.boundingBox ?: continue
            val raw = block.text.trim().replace("\n", " ")
            val name = raw
                .replace(Regex("[^A-Za-z0-9_А-Яа-яЁё-]"), "")

            if (!looksLikePlayerName(name, box, current.width, current.height)) {
                continue
            }

            val localMotion = motionAround(box, current, old)
            val score = behaviorScore(localMotion)

            out.add(
                LiveMarker(
                    name = name.take(24),
                    x = box.centerX() * scaleX,
                    y = box.bottom * scaleY,
                    score = score
                )
            )
        }

        return out.distinctBy { it.name }.take(8)
    }

    private fun looksLikePlayerName(
        name: String,
        box: Rect,
        width: Int,
        height: Int
    ): Boolean {
        if (name.length !in 3..20) return false
        if (box.width() < 10 || box.height() < 4) return false
        if (box.centerY() < height * 0.08f || box.centerY() > height * 0.90f) {
            return false
        }
        if (box.left < width * 0.01f || box.right > width * 0.99f) {
            return false
        }

        val validChars = name.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        return validChars && name.any { it.isLetter() }
    }

    private fun motionAround(
        box: Rect,
        current: Bitmap,
        old: Bitmap?
    ): Double {
        if (old == null || old.width != current.width || old.height != current.height) {
            return 0.0
        }

        val padX = max(8, box.width())
        val padY = max(10, box.height() * 2)
        val left = max(0, box.left - padX)
        val top = max(0, box.top - padY)
        val right = min(current.width - 1, box.right + padX)
        val bottom = min(current.height - 1, box.bottom + padY)

        var total = 0L
        var count = 0L

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val a = current.getPixel(x, y)
                val b = old.getPixel(x, y)

                val ca =
                    ((a shr 16) and 255) +
                    ((a shr 8) and 255) +
                    (a and 255)

                val cb =
                    ((b shr 16) and 255) +
                    ((b shr 8) and 255) +
                    (b and 255)

                total += abs(ca - cb)
                count++

                x += 6
            }
            y += 6
        }

        return if (count == 0L) 0.0
        else total.toDouble() / count / 765.0
    }

    private fun behaviorScore(motion: Double): Int =
        when {
            motion > 0.42 -> 78
            motion > 0.28 -> 55
            motion > 0.15 -> 35
            else -> 15
        }

    private fun saveObservations(markers: List<LiveMarker>) {
        if (markers.isEmpty() || closed) return

        try {
            val arr = JSONArray(prefs.getString("records", "[]"))
            val map = linkedMapOf<String, JSONObject>()

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isNotEmpty()) {
                    map[name.lowercase(Locale.ROOT)] = o
                }
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
                val newScore =
                    ((oldScore * oldObs) + m.score) / (oldObs + 1)

                o.put("score", newScore)
                o.put("observations", oldObs + 1)
                o.put("lastSeen", now)

                val signals = o.optJSONArray("signals") ?: JSONArray()
                if (m.score >= 70) {
                    signals.put("резкие/аномальные движения")
                } else if (m.score >= 35) {
                    signals.put("необычное движение")
                }
                o.put("signals", signals)

                map[key] = o
            }

            val out = JSONArray()
            map.values.forEach { out.put(it) }
            prefs.edit().putString("records", out.toString()).apply()
        } catch (_: Throwable) {
            // Storage errors must not terminate the analyzer.
        }
    }

    fun close() {
        if (closed) return
        closed = true

        try {
            recognizer.close()
        } catch (_: Throwable) {
        }

        previous?.recycleSafely()
        previous = null

        executor.shutdownNow()
        busy = false
    }

    private fun Bitmap.recycleSafely() {
        try {
            if (!isRecycled) recycle()
        } catch (_: Throwable) {
        }
    }
}
