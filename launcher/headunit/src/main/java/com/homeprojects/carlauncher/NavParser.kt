package com.homeprojects.carlauncher

import java.util.Locale

/** One turn-by-turn step, as shown on the white navigation card. */
data class NavInfo(
    val distance: String?,   // "250m", "1.2km"
    val maneuver: String?,   // "TURN LEFT"; null when the notification doesn't say
    val road: String?,       // "Jl. Jend. Sudirman (Semanggi)"
    val etaMin: Int?,        // minutes to destination
)

private val DISTANCE = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m|mi|ft)\b""", RegexOption.IGNORE_CASE)
private val HOURS = Regex("""(\d+)\s?(?:hr|h|jam)\b""", RegexOption.IGNORE_CASE)
private val MINUTES = Regex("""(\d+)\s?(?:min|mnt|menit)\b""", RegexOption.IGNORE_CASE)
// Longer phrases first: alternation takes the first one that matches at a position.
private val CONNECTOR = Regex("""\s(?:onto|towards|toward|to|ke arah|menuju|ke)\s""", RegexOption.IGNORE_CASE)

// Checked in order: the first phrase found names the maneuver. English and Indonesian Maps.
private val MANEUVERS = listOf(
    "slight left" to "SLIGHT LEFT", "sedikit ke kiri" to "SLIGHT LEFT",
    "slight right" to "SLIGHT RIGHT", "sedikit ke kanan" to "SLIGHT RIGHT",
    "sharp left" to "SHARP LEFT", "tajam ke kiri" to "SHARP LEFT",
    "sharp right" to "SHARP RIGHT", "tajam ke kanan" to "SHARP RIGHT",
    "keep left" to "KEEP LEFT", "tetap di kiri" to "KEEP LEFT", "tetap di jalur kiri" to "KEEP LEFT",
    "keep right" to "KEEP RIGHT", "tetap di kanan" to "KEEP RIGHT", "tetap di jalur kanan" to "KEEP RIGHT",
    "u-turn" to "U-TURN", "putar balik" to "U-TURN",
    "turn left" to "TURN LEFT", "belok kiri" to "TURN LEFT",
    "turn right" to "TURN RIGHT", "belok kanan" to "TURN RIGHT",
    "roundabout" to "ROUNDABOUT", "bundaran" to "ROUNDABOUT",
    "exit" to "EXIT", "keluar" to "EXIT",
    "merge" to "MERGE", "bergabung" to "MERGE",
    "arrive" to "ARRIVE", "destination" to "ARRIVE", "tiba" to "ARRIVE", "tujuan" to "ARRIVE",
    "continue" to "CONTINUE", "head " to "CONTINUE", "lanjutkan" to "CONTINUE", "lurus" to "CONTINUE",
)

/**
 * Reads Google Maps' ongoing navigation notification. Its layout differs by app version and language
 * (the distance can be in the title or the text, the instruction may be missing), so every field is
 * found by pattern rather than by position. Returns null when it doesn't look like a navigation step.
 */
fun parseMapsNotification(title: String?, text: String?, subText: String?): NavInfo? {
    val t = title?.trim().orEmpty()
    val x = text?.trim().orEmpty()
    val sub = subText?.trim().orEmpty()
    val distanceMatch = DISTANCE.find(t) ?: DISTANCE.find(x)
    val all = "$t $x".lowercase(Locale.ROOT)
    val maneuver = MANEUVERS.firstOrNull { all.contains(it.first) }?.second
    if (distanceMatch == null && maneuver == null) return null

    val distance = distanceMatch?.let {
        it.groupValues[1].replace(',', '.') + it.groupValues[2].lowercase(Locale.ROOT)
    }
    // The road is whatever is left once the distance and the instruction are taken out. Prefer the
    // text line; "Turn left onto X" keeps only X.
    val road = listOf(x, t).firstNotNullOfOrNull { roadFrom(it) }
    return NavInfo(distance, maneuver, road, etaMinutes(sub))
}

private fun roadFrom(line: String): String? {
    var s = DISTANCE.replace(line, "")
    val connector = CONNECTOR.find(s)
    if (connector != null) {
        s = s.substring(connector.range.last + 1)
    } else if (MANEUVERS.any { s.lowercase(Locale.ROOT).contains(it.first) }) {
        return null   // only an instruction, no road named
    }
    s = s.trim(' ', '·', '-', '–', ',', '•')
    return s.ifEmpty { null }
}

/** "14 min · 5.2 km · 20:40 ETA" -> 14; "1 hr 5 min" -> 65. Null when no duration is given. */
fun etaMinutes(subText: String): Int? {
    val firstPart = subText.split('·', '•', '|').firstOrNull().orEmpty()
    val h = HOURS.find(firstPart)?.groupValues?.get(1)?.toInt()
    val m = MINUTES.find(firstPart)?.groupValues?.get(1)?.toInt()
    if (h == null && m == null) return null
    return (h ?: 0) * 60 + (m ?: 0)
}
