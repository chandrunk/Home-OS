package com.homeprojects.carlauncher.ui.rivian

import android.media.AudioManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homeprojects.carlauncher.AppEntry
import com.homeprojects.carlauncher.CarHeadlightDetector
import com.homeprojects.carlauncher.MediaHub
import com.homeprojects.carlauncher.NativeAppLauncher
import com.homeprojects.carlauncher.R
import com.homeprojects.carlauncher.Shortcut
import com.homeprojects.carlauncher.Shortcuts
import com.homeprojects.carlauncher.ui.LIcon
import com.homeprojects.carlauncher.ui.Overlay
import com.homeprojects.carlauncher.ui.Track
import com.homeprojects.carlauncher.ui.Txt
import com.homeprojects.carlauncher.ui.Ui
import com.homeprojects.carlauncher.ui.dragFraction
import com.homeprojects.carlauncher.ui.open
import com.homeprojects.carlauncher.ui.pick
import com.homeprojects.carlauncher.ui.rememberNow
import com.homeprojects.carlauncher.ui.surface
import com.homeprojects.carlauncher.ui.theme.Look
import com.homeprojects.carlauncher.ui.theme.Rv
import com.homeprojects.carlauncher.ui.theme.Sans
import com.homeprojects.carlauncher.ui.theme.Skin
import com.homeprojects.carlauncher.ui.theme.ThemeMode
import com.homeprojects.carlauncher.ui.topHairline
import com.homeprojects.carlauncher.ui.touch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Full-screen Rivian sheet: eyebrow, title, a close pill, content, optional footer. */
@Composable
private fun Sheet(
    eyebrow: String,
    title: String,
    close: String = "Close",
    footerLeft: String? = null,
    footerRight: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalR.current
    Column(
        Modifier.fillMaxSize().background(p.canvas)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 24.dp)) {
                Txt(eyebrow, 15, Sans, color = p.accent, weight = FontWeight.ExtraBold, tracking = Track.WIDEST)
                Txt(title, 32, Sans, Modifier.padding(top = 2.dp), p.text, FontWeight.ExtraBold)
            }
            Row(
                Modifier.height(62.dp).touch { Ui.overlay = Overlay.None }.rCard(p.card, 20, p).padding(horizontal = 26.dp),
                Arrangement.spacedBy(10.dp), Alignment.CenterVertically,
            ) {
                LIcon(R.drawable.lc_x, 24, tint = p.text)
                Txt(close, 18, Sans, color = p.text, weight = FontWeight.ExtraBold)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        content()
        if (footerLeft != null || footerRight != null) {
            Row(Modifier.fillMaxWidth().topHairline(p.border).padding(top = 16.dp), Arrangement.SpaceBetween) {
                Txt(footerLeft.orEmpty(), 15, Sans, Modifier.weight(1f).padding(end = 24.dp), p.text2, FontWeight.Bold)
                Txt(footerRight.orEmpty(), 15, Sans, color = p.text2, weight = FontWeight.Bold)
            }
        }
    }
}

// ---- Display: theme mode, interface, volume, headlight threshold -------------------------------

@Composable
fun RivianDisplay() {
    val p = LocalR.current
    val b = CarHeadlightDetector.brightness
    Sheet(
        "VEHICLE DISPLAY & SOUND", "Appearance & Headlight Sync", close = "Done",
        footerLeft = "HEADLIGHT DETECTOR • uiMode ${if (CarHeadlightDetector.uiNight) "night" else "day"} • " +
            "backlight ${if (b >= 0) "${b * 100 / 255}%" else "n/a"}",
        footerRight = "RIVIAN THEME",
    ) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Txt("THEME MODE", 16, Sans, color = p.text2, weight = FontWeight.ExtraBold, tracking = Track.WIDER)
            Row(Modifier.rCard(p.elevated, 16, p).padding(4.dp), Arrangement.spacedBy(4.dp)) {
                Txt("INTERFACE", 15, Sans, Modifier.align(Alignment.CenterVertically).padding(horizontal = 10.dp), p.text2, FontWeight.Bold)
                SkinChip("Nothing", Skin.NOTHING)
                SkinChip("Rivian", Skin.RIVIAN)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ModeCard(ThemeMode.AUTO, R.drawable.lc_car_front, "Auto (Car Lights)",
                "Night when the headlights dim the screen, or after sunset.", recommended = true)
            ModeCard(ThemeMode.DAY, R.drawable.lc_sun, "Day Mode (Warm Sand)", "Warm sand surfaces for daytime readability.")
            ModeCard(ThemeMode.NIGHT, R.drawable.lc_moon, "Night Mode (Forest Slate)", "Deep slate surfaces, no cabin glare at night.")
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VolumeCard()
            ThresholdCard()
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SkinChip(label: String, skin: Skin) {
    val p = LocalR.current
    val active = Look.skin == skin
    Box(
        Modifier.height(40.dp).touch { Look.set(skin = skin) }
            .then(if (active) Modifier.surface(Rv.Yellow, 12, border = false) else Modifier)
            .padding(horizontal = 16.dp),
        Alignment.Center,
    ) {
        Txt(label, 15, Sans, color = if (active) Rv.OnYellow else p.text, weight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RowScope.ModeCard(mode: ThemeMode, icon: Int, title: String, description: String, recommended: Boolean = false) {
    val p = LocalR.current
    val active = Look.mode == mode
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.weight(1f).height(180.dp).touch { Look.set(mode = mode) }.clip(shape).background(p.card)
            .border(if (active) 2.5.dp else 1.dp, if (active) p.accent else p.border, shape).padding(22.dp),
        Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).surface(if (active) p.accent.copy(alpha = 0.18f) else p.elevated, 16, border = false),
                Alignment.Center,
            ) { LIcon(icon, 28, tint = if (active) p.accent else p.text) }
            if (recommended) {
                Box(Modifier.surface(Rv.Yellow, 999, border = false).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Txt("RECOMMENDED", 15, Sans, color = Rv.OnYellow, weight = FontWeight.ExtraBold)
                }
            }
        }
        Column {
            Txt(title, 22, Sans, color = p.text, weight = FontWeight.ExtraBold)
            Txt(description, 15, Sans, Modifier.padding(top = 4.dp), p.text2, FontWeight.SemiBold, maxLines = 2)
        }
    }
}

@Composable
private fun RowScope.SliderCard(icon: Int, title: String, value: String, fraction: Float, note: String?, onChange: (Float) -> Unit) {
    val p = LocalR.current
    Row(
        Modifier.weight(1f).rCard(p.card, 24, p).padding(22.dp),
        Arrangement.spacedBy(20.dp), Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).surface(p.elevated, 16, border = false), Alignment.Center) { LIcon(icon, 28, tint = p.text) }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), Arrangement.SpaceBetween) {
                Txt(title, 15, Sans, Modifier.weight(1f).padding(end = 12.dp), p.text, FontWeight.ExtraBold)
                Txt(value, 15, Sans, color = p.accent, weight = FontWeight.ExtraBold)
            }
            // A 28 dp touch band around the 10 dp track, so a finger at speed still lands on it.
            Box(Modifier.fillMaxWidth().height(28.dp).dragFraction(title, onChange), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(p.elevated)) {
                    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(p.accent, CircleShape))
                }
            }
            note?.let { Txt(it, 15, Sans, Modifier.padding(top = 6.dp), p.text2, FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun RowScope.VolumeCard() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 30 }
    var volume by remember { mutableIntStateOf(audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    SliderCard(R.drawable.lc_volume_2, "Master Volume (Media & Nav)", "${volume * 100 / max}%", volume.toFloat() / max, null) { f ->
        val v = (f * max).roundToInt()
        if (v != volume) {
            volume = v
            runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        }
    }
}

/** The ILL calibration knob: every unit dims to a different level, so the driver sets where night starts. */
@Composable
private fun RowScope.ThresholdCard() {
    val b = CarHeadlightDetector.brightness
    SliderCard(
        R.drawable.lc_gauge, "Headlight Brightness Threshold", "< ${Look.threshold * 100 / 255}% backlight",
        Look.threshold / 255f,
        if (b >= 0) "Backlight now ${b * 100 / 255}%: ${if (b < Look.threshold) "night" else "day"}" else "This unit doesn't report its backlight",
    ) { f -> Look.set(threshold = (f * 255).roundToInt()) }
}

// ---- All applications and the app picker -------------------------------------------------------

@Composable
fun RivianDrawer() {
    val context = LocalContext.current
    val version = Ui.appsVersion
    val shortcuts = Shortcuts.directory
    val others = remember(version) {
        val taken = shortcuts.mapNotNull { runCatching { NativeAppLauncher.resolve(context, it) }.getOrNull() }.toSet()
        runCatching { NativeAppLauncher.launchableApps(context) }.getOrDefault(emptyList()).filter { it.packageName !in taken }
    }
    Sheet("VEHICLE APPS & UTILITIES", "All Applications", footerLeft = "Hold a shortcut to choose its app",
        footerRight = "${shortcuts.size + others.size} apps") {
        LazyVerticalGrid(
            GridCells.Fixed(5), Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(shortcuts.size) { i ->
                val s = shortcuts[i]
                AppTile(s.label, onLongClick = { pick(s) }, onClick = { Ui.overlay = Overlay.None; open(context, s) },
                    box = if (s == Shortcuts.ZLINK) Rv.NavCard else null) { tint -> LIcon(s.icon, 34, tint = tint) }
            }
            items(others, key = { it.packageName }) { app ->
                AppTile(app.label, onClick = {
                    Ui.overlay = Overlay.None
                    NativeAppLauncher.launchPackage(context, app.packageName)
                }) { AppIcon(app) }
            }
        }
    }
}

@Composable
fun RivianPicker(shortcut: Shortcut) {
    val context = LocalContext.current
    val apps = remember { runCatching { NativeAppLauncher.launchableApps(context) }.getOrDefault(emptyList()) }
    Sheet("CHOOSE AN APP", shortcut.label, footerLeft = "Tap the app this button should open", footerRight = "${apps.size} apps") {
        LazyVerticalGrid(
            GridCells.Fixed(5), Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(app.label, onClick = {
                    NativeAppLauncher.save(context, shortcut, app.packageName)
                    Ui.appsVersion++
                    Ui.overlay = Overlay.None
                    Ui.toast("${shortcut.label} → ${app.label}")
                    NativeAppLauncher.launchPackage(context, app.packageName)
                }) { AppIcon(app) }
            }
        }
    }
}

@Composable
private fun AppTile(
    label: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    box: Color? = null,
    icon: @Composable (Color) -> Unit,
) {
    val p = LocalR.current
    Column(
        Modifier.fillMaxWidth().height(176.dp).touch(onLongClick, onClick).rCard(p.card, 26, p),
        Arrangement.spacedBy(14.dp, Alignment.CenterVertically), Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(68.dp).surface(box ?: p.elevated, 22, border = false), Alignment.Center) {
            icon(if (box != null) Color.White else p.text)
        }
        Txt(label, 17, Sans, Modifier.padding(horizontal = 12.dp), p.text, FontWeight.ExtraBold)
    }
}

@Composable
private fun AppIcon(app: AppEntry) {
    val icon = app.icon
    if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(40.dp))
    else LIcon(R.drawable.lc_layout_grid, 34, tint = LocalR.current.text)
}

// ---- Calm: the dim night clock ------------------------------------------------------------------

@Composable
fun RivianCalm() {
    val now = rememberNow(60_000)
    val info by MediaHub.info.collectAsState()
    Box(
        Modifier.fillMaxSize().background(Rv.CalmBg)
            .clickable(remember { MutableInteractionSource() }, indication = null) { Ui.overlay = Overlay.None }
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            // Dim on purpose, but readable: 3.6:1 for the time, 3.2:1 for the hint.
            Txt(SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(now)), 112, Sans, color = Color(0xFF5E6B71),
                weight = FontWeight.ExtraBold, tracking = Track.TIGHT, tight = true)
            info?.takeIf { it.title.isNotBlank() }?.let { m ->
                val artist = m.artist.substringBefore(" • ")
                Row(
                    Modifier.padding(top = 20.dp).widthIn(max = 900.dp).surface(Color(0xFF111619), 999, true, Color(0xFF1E262B))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(Rv.Yellow, CircleShape))
                    Txt("${if (artist.isBlank()) "" else "$artist — "}${m.title}", 18, Sans, Modifier.weight(1f, fill = false),
                        Color(0xFF6C7A80), FontWeight.Bold)
                }
            }
        }
        Txt("TAP ANYWHERE TO WAKE DISPLAY", 15, Sans, Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            Color(0xFF56636A), FontWeight.Bold, Track.WIDEST)
    }
}
