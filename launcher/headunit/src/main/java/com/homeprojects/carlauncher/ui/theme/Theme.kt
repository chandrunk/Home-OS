package com.homeprojects.carlauncher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.homeprojects.carlauncher.R

/** After sunset the big white surfaces glare in a dark cabin: they dim, and the nav card turns dark. */
object Night {
    var on by mutableStateOf(false)
}

/**
 * Nothing OS matte palette, from the design's Tailwind config. Zero blur, zero shadows.
 * Red is the one accent: the turn instruction, the playhead and the speed unit, nothing decorative.
 */
object N {
    val Black = Color(0xFF0D0D0F)     // canvas
    val Card = Color(0xFF16161A)      // surface level 1
    val Elevated = Color(0xFF222228)  // surface level 2 (buttons)
    val Border = Color(0xFF2C2C34)    // 1 dp hairline
    val White: Color get() = if (Night.on) Color(0xFFCFCFCB) else Color(0xFFF2F2F0)  // active surface / text
    val Red = Color(0xFFE52521)       // signature accent
    val Muted = Color(0xFF8E8E98)     // secondary text
    val Dim = Color(0xFF4A4A52)       // inactive / technical
    val Ink = Color(0xFF333338)       // road name on the white nav card
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
