package com.homeprojects.carlauncher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.homeprojects.carlauncher.R
import com.homeprojects.carlauncher.ui.Overlay
import com.homeprojects.carlauncher.ui.Ui

/** After sunset the big white surfaces glare in a dark cabin: they dim, and the nav card turns dark. */
object Night {
    var on by mutableStateOf(false)
}

/**
 * Nothing OS palette. Monochrome, so day is the night palette turned over: light canvas and white
 * cards, black text and black active surfaces (a dark screen mirrors the sky at noon). Night is the
 * matte dark original with its whites dimmed. Red is the one accent either way: the turn
 * instruction, the playhead and the speed unit. The ambient clock stays dark: it is a rest screen.
 */
object N {
    /** Day palette in effect. */
    private val light: Boolean get() = !Night.on && Ui.overlay != Overlay.Ambient

    private fun pick(day: Long, night: Long) = Color(if (light) day else night)

    // No pure white anywhere: by day the brightest surface is a warm grey, by night the "white" is
    // dimmed too. A head unit at full white is the brightest thing in the cabin either way.
    val Black: Color get() = pick(0xFFD9D9D4, 0xFF0D0D0F)     // canvas; ink on active surfaces
    val Card: Color get() = pick(0xFFE6E6E1, 0xFF16161A)      // surface level 1
    val Elevated: Color get() = pick(0xFFCDCDC7, 0xFF222228)  // surface level 2 (buttons)
    val Border: Color get() = pick(0xFFBEBEB8, 0xFF2C2C34)    // 1 dp hairline
    /** Text, icons and active surfaces. */
    val White: Color get() = when {
        light -> Color(0xFF121214)
        Night.on -> Color(0xFFB4B4AF)   // 8.7:1 on the cards, without glare
        else -> Color(0xFFF2F2F0)       // the ambient clock by day
    }
    /** Signature accent. Darker by day: 4.8:1 on the grey cards, 3.1:1 (large text) on the nav card. */
    val Red: Color get() = pick(0xFFC0221C, 0xFFE52521)
    val Muted: Color get() = pick(0xFF55555D, 0xFF8E8E98)     // secondary text
    val Dim: Color get() = pick(0xFF9A9AA1, 0xFF4A4A52)       // inactive / technical
    val Ink: Color get() = pick(0xFFC4C4C8, 0xFF333338)       // road name on the active (nav) card
    val AmbientBg = Color(0xFF0A0A0C)
    val AmbientPill = Color(0xFF18181C)
    val AmbientDial = Color(0xFF141418)
}

/** The design is drawn for a 1280 x 720 px screen; MainActivity maps 1 design px to 1 dp. */
const val DESIGN_W = 1280
const val DESIGN_H = 720

/**
 * Doto with round dots (a static ROND=100, wght=800 instance), the closest open font to Nothing's NDot.
 * Only for big display moments (overlay titles, the ambient clock): dot-matrix is hard to read small.
 */
val Dot = FontFamily(Font(R.font.doto_round, FontWeight.ExtraBold))

// Static files per weight: several weights of one variable font file can resolve to the same cached
// typeface (and get faux-bolded), and Android 8 head units are uneven with variable fonts.

/** Plus Jakarta Sans: road names and track titles, readable at a glance. */
val Sans = FontFamily(
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** JetBrains Mono: technical labels (VELOCITY, HEADING) and the speedometer. */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold),
)
