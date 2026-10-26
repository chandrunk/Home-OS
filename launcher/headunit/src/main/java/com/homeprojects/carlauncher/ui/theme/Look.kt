package com.homeprojects.carlauncher.ui.theme

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The two interfaces. Same screens and features, different look; the inactive one costs nothing. */
enum class Skin { NOTHING, RIVIAN }

/** Day, night, or follow the car: headlights (ILL wire) or, failing that, sunset. */
enum class ThemeMode { AUTO, DAY, NIGHT }

/** The driver's display choices, saved across restarts. */
object Look {
    private const val PREFS = "look"

    var skin by mutableStateOf(Skin.NOTHING)
        private set
    var mode by mutableStateOf(ThemeMode.AUTO)
        private set
    /** Night when the screen brightness setting drops below this (0..255); 115 is about 45%. */
    var threshold by mutableIntStateOf(115)
        private set

    private var context: Context? = null

    fun load(context: Context) {
        this.context = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        skin = runCatching { Skin.valueOf(p.getString("skin", null)!!) }.getOrDefault(Skin.NOTHING)
        mode = runCatching { ThemeMode.valueOf(p.getString("mode", null)!!) }.getOrDefault(ThemeMode.AUTO)
        threshold = p.getInt("threshold", 115)
    }

    fun set(skin: Skin = this.skin, mode: ThemeMode = this.mode, threshold: Int = this.threshold) {
        this.skin = skin
        this.mode = mode
        this.threshold = threshold.coerceIn(0, 255)
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("skin", skin.name)?.putString("mode", mode.name)?.putInt("threshold", this.threshold)?.apply()
    }

    fun cycleMode() = set(mode = ThemeMode.entries[(mode.ordinal + 1) % ThemeMode.entries.size])
}

/** Rivian palette. Yellow is only ever a fill (dark ink on it) or, by night, an accent on dark. */
@Immutable
data class RPalette(
    val canvas: Color,
    val card: Color,
    val elevated: Color,
    val pill: Color,
    val border: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    /** Accent as text or icon on a card: yellow by night; by day yellow on sand is 1.8:1, so dark amber. */
    val accent: Color,
)

object Rv {
    val Yellow = Color(0xFFF5A623)     // fills: play, Home, chips
    val OnYellow = Color(0xFF14191C)   // 8.7:1 on yellow
    val Green = Color(0xFF2E7D47)      // navigation and status only
    val NavCard = Color(0xFF1F4E32)
    val NavBox = Color(0xFF153823)
    val NavRoad = Color(0xFFD1E7D8)
    val NavLabel = Color(0xFF8AB89A)
    val CalmBg = Color(0xFF080B0C)

    val Day = RPalette(
        canvas = Color(0xFFEAE7DF), card = Color(0xFFF5F3EE), elevated = Color(0xFFE4E1D8),
        pill = Color(0xFFD8D4C8), border = Color(0xFFD5D1C5), text = Color(0xFF14191C),
        text2 = Color(0xFF525B60), muted = Color(0xFF828A8E), accent = Color(0xFF9A6200),
    )
    val Night = RPalette(
        canvas = Color(0xFF0E1214), card = Color(0xFF161C1F), elevated = Color(0xFF20282C),
        pill = Color(0xFF2A3439), border = Color(0xFF283237), text = Color(0xFFF3F5F6),
        text2 = Color(0xFF9BA6AC), muted = Color(0xFF5C686E), accent = Yellow,
    )
}

/** The current Rivian palette, cross-fading in 220 ms when day turns to night. */
@Composable
fun rivianPalette(): RPalette {
    val target = if (Night.on) Rv.Night else Rv.Day
    @Composable fun a(c: Color) = animateColorAsState(c, tween(220), label = "theme").value
    return RPalette(
        canvas = a(target.canvas), card = a(target.card), elevated = a(target.elevated), pill = a(target.pill),
        border = a(target.border), text = a(target.text), text2 = a(target.text2), muted = a(target.muted),
        accent = a(target.accent),
    )
}
