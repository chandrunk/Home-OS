package com.homeprojects.carlauncher.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homeprojects.carlauncher.AppEntry
import com.homeprojects.carlauncher.MediaHub
import com.homeprojects.carlauncher.NativeAppLauncher
import com.homeprojects.carlauncher.R
import com.homeprojects.carlauncher.Shortcut
import com.homeprojects.carlauncher.Shortcuts
import com.homeprojects.carlauncher.ui.theme.Dot
import com.homeprojects.carlauncher.ui.theme.Mono
import com.homeprojects.carlauncher.ui.theme.N
import com.homeprojects.carlauncher.ui.theme.Sans
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Full-screen Nothing overlay: section label, dot-matrix title, CLOSE pill, content, footer. */
@Composable
private fun OverlayFrame(
    section: String,
    title: String,
    footerLeft: String? = null,
    footerRight: String? = null,
    onClose: () -> Unit = { Ui.overlay = Overlay.None },
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(N.Black)
            // swallow taps so nothing underneath reacts
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 24.dp)) {
                Txt("NOTHING CAR OS // $section", 15, Mono, color = N.Muted, tracking = Track.WIDEST)
                Txt(title, 40, Dot, Modifier.padding(top = 6.dp), tight = true)
            }
            Row(
                Modifier.height(60.dp).touch(onClick = onClose).surface(N.White, 999, border = false)
                    .padding(horizontal = 24.dp),
                Arrangement.spacedBy(10.dp), Alignment.CenterVertically,
            ) {
                LIcon(R.drawable.lc_x, 22, tint = N.Black)
                Txt("CLOSE", 16, Sans, color = N.Black, weight = FontWeight.ExtraBold)
            }
        }
        content()
        if (footerLeft != null || footerRight != null) {
            Row(Modifier.fillMaxWidth().topHairline().padding(top = 14.dp), Arrangement.SpaceBetween) {
                Txt(footerLeft.orEmpty(), 15, Mono, Modifier.weight(1f).padding(end = 24.dp), N.Muted)
                Txt(footerRight.orEmpty(), 15, Mono, color = N.Muted)
            }
        }
    }
}

private fun installedApps(context: Context): List<AppEntry> =
    runCatching { NativeAppLauncher.launchableApps(context) }.getOrDefault(emptyList())

// ---- 02 ALL APPS -------------------------------------------------------------------------------

@Composable
fun AppDrawer() {
    val context = LocalContext.current
    val version = Ui.appsVersion
    val shortcuts = Shortcuts.directory
    val others = remember(version) {
        val taken = shortcuts.mapNotNull { runCatching { NativeAppLauncher.resolve(context, it) }.getOrNull() }.toSet()
        installedApps(context).filter { it.packageName !in taken }
    }
    OverlayFrame("02 DIRECTORY", "ALL APPS", "LONG-PRESS A SHORTCUT TO CHOOSE ITS APP", "02 / APPS DIRECTORY") {
        LazyVerticalGrid(
            GridCells.Fixed(5),
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 96.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(shortcuts.size) { i ->
                val s = shortcuts[i]
                DirectoryTile(s.label, highlighted = i == 0, onLongClick = { pick(s) }, onClick = {
                    Ui.overlay = Overlay.None
                    open(context, s)
                }) { fg -> LIcon(s.icon, 32, tint = fg) }
            }
            items(others, key = { it.packageName }) { app ->
                DirectoryTile(app.label, highlighted = false, onClick = {
                    Ui.overlay = Overlay.None
                    NativeAppLauncher.launchPackage(context, app.packageName)
                }) { AppIcon(app) }
            }
        }
    }
}

@Composable
private fun DirectoryTile(
    label: String,
    highlighted: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(176.dp).touch(onLongClick, onClick)
            .surface(if (highlighted) N.White else N.Card, 22, border = !highlighted),
        Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).background(if (highlighted) N.Black else N.White, CircleShape), Alignment.Center) {
                icon(if (highlighted) N.White else N.Black)
            }
            Txt(label, 17, Sans, Modifier.padding(horizontal = 12.dp), if (highlighted) N.Black else N.White,
                if (highlighted) FontWeight.ExtraBold else FontWeight.Bold)
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry) {
    val icon = app.icon
    if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(36.dp), colorFilter = Greyscale)
    else LIcon(R.drawable.lc_grip, 32, tint = N.Black)
}

/** Choose the app a shortcut opens (radio, Bluetooth phone, DSP... differ per head unit). */
@Composable
fun AppPicker(shortcut: Shortcut) {
    val context = LocalContext.current
    val apps = remember { installedApps(context) }
    OverlayFrame(
        "PICK APP", shortcut.label.uppercase(Locale.ROOT),
        "TAP THE APP THIS BUTTON SHOULD OPEN", "${apps.size} APPS",
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(5),
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                DirectoryTile(app.label, highlighted = false, onClick = {
                    NativeAppLauncher.save(context, shortcut, app.packageName)
                    Ui.appsVersion++
                    Ui.overlay = Overlay.None
                    Ui.toast("${shortcut.label.uppercase(Locale.ROOT)} → ${app.label.uppercase(Locale.ROOT)}")
                    NativeAppLauncher.launchPackage(context, app.packageName)
                }) { AppIcon(app) }
            }
        }
    }
}

// ---- 03 QUICK SETTINGS -------------------------------------------------------------------------

@Composable
fun QuickSettings() {
    val context = LocalContext.current
    val btOn = remember {
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
    }
    OverlayFrame("03 SYSTEM", "QUICK SETTINGS") {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingTile("Wi-Fi Hotspot", if (Ui.wifi) "Connected" else "Not connected", R.drawable.lc_wifi, Ui.wifi) {
                openSettings(context, Settings.ACTION_WIFI_SETTINGS)
            }
            SettingTile("Bluetooth", if (btOn) "On" else "Off", R.drawable.lc_bluetooth, btOn) {
                openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            SettingTile("Radio", "Open Native App", R.drawable.lc_radio, false,
                onLongClick = { pick(Shortcuts.RADIO) }) { open(context, Shortcuts.RADIO) }
            SettingTile("Ambient Clock", "Night Screen Saver", R.drawable.lc_moon, false) {
                Ui.overlay = Overlay.Ambient
            }
        }
        Spacer(Modifier.weight(1f))
        VolumeBar()
    }
}

private fun openSettings(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Ui.toast("NOT AVAILABLE ON THIS UNIT") }
}

@Composable
private fun RowScope.SettingTile(
    title: String, sub: String, icon: Int, active: Boolean, iconTint: Color? = null,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Box(
        Modifier.weight(1f).height(136.dp).touch(onLongClick, onClick)
            .surface(if (active) N.White else N.Card, 22, border = !active),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), Arrangement.SpaceBetween) {
            LIcon(icon, 28, tint = iconTint ?: if (active) N.Black else N.White)
            Column {
                Txt(title, 18, Sans, color = if (active) N.Black else N.White,
                    weight = if (active) FontWeight.ExtraBold else FontWeight.Bold)
                Txt(sub, 15, Sans, color = if (active) N.Dim else N.Muted,
                    weight = if (active) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Live AudioManager.STREAM_MUSIC slider: tap or drag along the track. */
@Composable
private fun VolumeBar() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 30 }
    var volume by remember { mutableIntStateOf(audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    fun set(fraction: Float) {
        val v = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        if (v != volume) {
            volume = v
            runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        }
    }
    Row(
        Modifier.fillMaxWidth().surface(N.Card, 22).padding(24.dp),
        Arrangement.spacedBy(24.dp), Alignment.CenterVertically,
    ) {
        LIcon(R.drawable.lc_volume_2, 32)
        Column(Modifier.weight(1f)) {
            Txt("MASTER VOLUME (STREAM_MUSIC)", 15, Sans, Modifier.padding(bottom = 10.dp), N.Muted, FontWeight.Bold)
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(28.dp)
                    .pointerInput(max) { detectTapGestures { set(it.x / size.width) } }
                    .pointerInput(max) { detectHorizontalDragGestures { change, _ -> set(change.position.x / size.width) } },
                contentAlignment = Alignment.CenterStart,
            ) {
                val fraction = volume.toFloat() / max
                Box(Modifier.fillMaxWidth().height(8.dp).background(N.Elevated, CircleShape))
                Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(N.White, CircleShape))
                RedDot(20, Modifier.offset(x = maxWidth * fraction - 10.dp))
            }
        }
        Txt("$volume", 32, Mono, weight = FontWeight.Bold)
    }
}

// ---- AMBIENT ------------------------------------------------------------------------------------

@Composable
fun AmbientClock() {
    val now = rememberNow()
    val info by MediaHub.info.collectAsState()
    Column(
        Modifier.fillMaxSize().background(N.AmbientBg)
            .clickable(remember { MutableInteractionSource() }, indication = null) { Ui.overlay = Overlay.None }
            .padding(56.dp)
    ) {
        Txt("NOTHING CAR OS", 15, Mono, color = N.Muted, weight = FontWeight.Bold, tracking = Track.WIDEST)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            // Bounded, so a long "Now playing" line ends in "…" instead of pushing the dial off screen.
            Column(Modifier.weight(1f).padding(end = 48.dp)) {
                Txt(SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(now)), 128, Dot, tracking = Track.TIGHT, tight = true)
                Txt(
                    SimpleDateFormat("EEEE / d MMMM", Locale.US).format(Date(now)).uppercase(Locale.US),
                    20, Mono, Modifier.padding(top = 20.dp), N.Muted, FontWeight.Bold, Track.WIDEST,
                )
                info?.takeIf { it.title.isNotBlank() }?.let { m ->
                    Row(
                        Modifier.padding(top = 36.dp).surface(N.AmbientPill, 999).padding(horizontal = 28.dp, vertical = 16.dp),
                        Arrangement.spacedBy(14.dp), Alignment.CenterVertically,
                    ) {
                        RedDot(10, color = N.Muted)
                        val artist = m.artist.substringBefore(" • ")
                        Txt("Now playing / ${if (artist.isBlank()) "" else "$artist — "}${m.title}", 20, Sans,
                            Modifier.weight(1f, fill = false), weight = FontWeight.SemiBold)
                    }
                }
            }
            AnalogClock(now)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Txt("Tap anywhere to return", 18, Sans, color = N.Muted, weight = FontWeight.Medium)
            Txt("CLOCK / AMBIENT", 15, Mono, color = N.Muted, weight = FontWeight.Bold, tracking = Track.WIDEST)
        }
    }
}

/** Nothing analog dial: white hour hand, grey minute hand, red pivot, red 12 o'clock dot. */
@Composable
private fun AnalogClock(now: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val minutes = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    val hours = cal.get(Calendar.HOUR) + minutes / 60f
    Canvas(Modifier.size(320.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(N.AmbientDial, r)
        drawCircle(N.Border, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        val inset = 18.dp.toPx()
        drawCircle(N.White, 4.dp.toPx(), Offset(c.x, inset + 4.dp.toPx()))
        val small = 2.dp.toPx()
        drawCircle(N.Muted, small, Offset(c.x, size.height - inset - small))
        drawCircle(N.Muted, small, Offset(inset + small, c.y))
        drawCircle(N.Muted, small, Offset(size.width - inset - small, c.y))
        fun hand(turns: Float, length: Float, width: Float, color: Color) {
            val a = Math.toRadians((turns * 360f).toDouble())
            drawLine(color, c, Offset(c.x + length * sin(a).toFloat(), c.y - length * cos(a).toFloat()),
                width, StrokeCap.Round)
        }
        hand(hours / 12f, 90.dp.toPx(), 16.dp.toPx(), N.White)
        hand(minutes / 60f, 110.dp.toPx(), 4.dp.toPx(), N.Muted)
        drawCircle(N.Red, 9.dp.toPx(), c)
    }
}

