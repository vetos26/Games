package com.vetos26.galaxy

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One line of the best results table. */
class ScoreEntry(val score: Int, val stage: Int, val date: String)

/** Player settings, stored in SharedPreferences. */
class Settings(context: Context) {
    companion object {
        const val MAX_SCORES = 10
    }

    private val prefs = context.getSharedPreferences("galaxy", Context.MODE_PRIVATE)

    var sound = prefs.getBoolean("sound", true)
        set(v) { field = v; save() }
    var vibration = prefs.getBoolean("vibration", true)
        set(v) { field = v; save() }
    var autoFire = prefs.getBoolean("autoFire", false)
        set(v) { field = v; save() }
    /** false = arrow buttons, true = swipe strip */
    var swipeMode = prefs.getBoolean("swipeMode", false)
        set(v) { field = v; save() }
    /** true = FIRE on the left side (left-handed layout) */
    var leftHanded = prefs.getBoolean("leftHanded", false)
        set(v) { field = v; save() }
    /** 0 = small, 1 = medium, 2 = large */
    var buttonSize = prefs.getInt("buttonSize", 1)
        set(v) { field = v; save() }
    /** 0 = easy, 1 = normal, 2 = hard */
    var difficulty = prefs.getInt("difficulty", 1)
        set(v) { field = v; save() }

    var hiScore = prefs.getInt("hi", 5000)
        private set

    fun updateHiScore(score: Int) {
        if (score > hiScore) hiScore = score
    }

    /** Top-10 best results, sorted from best to worst. */
    val scores = ArrayList<ScoreEntry>()

    /** Place (1..10) of the last finished game in [scores], 0 = not in the table. */
    var lastRank = 0

    init {
        loadScores()
    }

    /** Adds a finished game to the table. @return place 1..10 or 0. */
    fun addResult(score: Int, stage: Int): Int {
        lastRank = 0
        if (score <= 0) return 0
        var idx = scores.indexOfFirst { score > it.score }
        if (idx < 0) idx = scores.size
        if (idx >= MAX_SCORES) return 0
        val date = SimpleDateFormat("dd.MM.yy", Locale.US).format(Date())
        scores.add(idx, ScoreEntry(score, stage, date))
        while (scores.size > MAX_SCORES) scores.removeAt(scores.size - 1)
        lastRank = idx + 1
        updateHiScore(score)
        save()
        return lastRank
    }

    fun resetHiScore() {
        hiScore = 5000
        scores.clear()
        lastRank = 0
        save()
    }

    private fun loadScores() {
        scores.clear()
        val raw = prefs.getString("scores", "") ?: ""
        for (item in raw.split(";")) {
            val p = item.split(",")
            if (p.size != 3) continue
            val sc = p[0].toIntOrNull() ?: continue
            val st = p[1].toIntOrNull() ?: continue
            scores.add(ScoreEntry(sc, st, p[2]))
        }
        scores.sortByDescending { it.score }
        while (scores.size > MAX_SCORES) scores.removeAt(scores.size - 1)
    }

    private fun scoresToString() = scores.joinToString(";") { "${it.score},${it.stage},${it.date}" }

    fun save() {
        prefs.edit()
            .putBoolean("sound", sound)
            .putBoolean("vibration", vibration)
            .putBoolean("autoFire", autoFire)
            .putBoolean("swipeMode", swipeMode)
            .putBoolean("leftHanded", leftHanded)
            .putInt("buttonSize", buttonSize)
            .putInt("difficulty", difficulty)
            .putInt("hi", hiScore)
            .putString("scores", scoresToString())
            .apply()
    }
}

/** Short vibration pulses: feedback for touch buttons, explosions, etc. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    var enabled = true

    fun pulse(ms: Long, amplitude: Int = 120) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // some devices throw without VIBRATE permission or on emulators — ignore
        }
    }
}
