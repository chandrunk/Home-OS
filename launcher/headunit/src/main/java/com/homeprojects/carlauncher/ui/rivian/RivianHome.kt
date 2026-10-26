package com.homeprojects.carlauncher.ui.rivian

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homeprojects.carlauncher.CarNotificationListener
import com.homeprojects.carlauncher.MediaHub
import com.homeprojects.carlauncher.MediaInfo
import com.homeprojects.carlauncher.MediaSource
import com.homeprojects.carlauncher.NativeAppLauncher
import com.homeprojects.carlauncher.NavHub
import com.homeprojects.carlauncher.NavInfo
import com.homeprojects.carlauncher.R
import com.homeprojects.carlauncher.Shortcut
import com.homeprojects.carlauncher.Shortcuts
import com.homeprojects.carlauncher.TelemetryManager
import com.homeprojects.carlauncher.TripMath
import com.homeprojects.carlauncher.WeatherRepo
import com.homeprojects.carlauncher.ui.LIcon
import com.homeprojects.carlauncher.ui.Overlay
import com.homeprojects.carlauncher.ui.Track
import com.homeprojects.carlauncher.ui.Txt
import com.homeprojects.carlauncher.ui.Ui
import com.homeprojects.carlauncher.ui.appLabel
import com.homeprojects.carlauncher.ui.clock
import com.homeprojects.carlauncher.ui.dotMatrix
import com.homeprojects.carlauncher.ui.layer
import com.homeprojects.carlauncher.ui.open
import com.homeprojects.carlauncher.ui.openListenerSettings
import com.homeprojects.carlauncher.ui.pick
import com.homeprojects.carlauncher.ui.rememberNow
import com.homeprojects.carlauncher.ui.surface
import com.homeprojects.carlauncher.ui.theme.DESIGN_H
import com.homeprojects.carlauncher.ui.theme.DESIGN_W
import com.homeprojects.carlauncher.ui.theme.Look
import com.homeprojects.carlauncher.ui.theme.Mono
import com.homeprojects.carlauncher.ui.theme.RPalette
import com.homeprojects.carlauncher.ui.theme.Rv
import com.homeprojects.carlauncher.ui.theme.Sans
import com.homeprojects.carlauncher.ui.theme.ThemeMode
import com.homeprojects.carlauncher.ui.theme.rivianPalette
import com.homeprojects.carlauncher.ui.topHairline
import com.homeprojects.carlauncher.ui.touch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Rivian palette for this frame (day or night, mid cross-fade). */
internal val LocalR = staticCompositionLocalOf { Rv.Night }

/** A Rivian card: flat colour, rounded, 1 dp border in the palette's border colour. No shadows. */
internal fun Modifier.rCard(color: Color, radius: Int, p: RPalette, border: Boolean = true) =
    surface(color, radius, border, p.border)

/** The Rivian interface: same zones and features as Nothing, warm and rounded. */
@Composable
fun RivianHome() {
    val p = rivianPalette()
    CompositionLocalProvider(LocalR provides p) {
        Box(Modifier.size(DESIGN_W.dp, DESIGN_H.dp).background(p.canvas)) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TelemetryZone()
                MediaZone()
                DriverDock()
            }
            when (val o = Ui.overlay) {
                Overlay.None -> Unit
                Overlay.Drawer -> RivianDrawer()
                Overlay.QuickSettings -> RivianDisplay()
                Overlay.Ambient -> RivianCalm()
                is Overlay.Picker -> RivianPicker(o.shortcut)
            }
            RivianToast(Modifier.align(Alignment.BottomStart).padding(24.dp))
        }
    }
}

// ---- Zone A: telemetry & navigation -------------------------------------------------------------

@Composable
private fun TelemetryZone() {
    val p = LocalR.current
    val context = LocalContext.current
    val t by TelemetryManager.state.collectAsState()
    rememberNow()
    val fix = t.hasFix()
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.width(656.dp).fillMaxHeight().layer().clip(shape).background(p.card).dotMatrix(p.border, 24)
            .border(1.dp, p.border, shape).padding(22.dp)
    ) {
        val nav by NavHub.state.collectAsState()
        val n = nav
        if (n != null) NavCard(n) { open(context, Shortcuts.MAPS) } else Cruising(fix) { open(context, Shortcuts.MAPS) }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                // GPS state in place of the design's DRIVE pill: an aftermarket unit can't read the gear.
                Row(
                    Modifier.padding(bottom = 8.dp).rCard(p.elevated, 999, p).padding(horizontal = 14.dp, vertical = 6.dp),
                    Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(if (fix) Rv.Green else p.muted, CircleShape))
                    Txt(if (fix) "GPS SPEED" else "NO GPS FIX", 15, Sans, color = p.text2, weight = FontWeight.Bold, tracking = Track.WIDER)
                }
                Txt(if (fix) "${t.speedKmh ?: 0}" else "--", 148, Sans, Modifier.layer(), color = p.text, weight = FontWeight.ExtraBold,
                    tracking = Track.TIGHT, tight = true)
                Txt("KM/H", 20, Sans, Modifier.padding(top = 6.dp), p.text2, FontWeight.ExtraBold, 0.15f)
            }
            Column(Modifier.width(236.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("HEADING", t.bearing?.let(::headingWords) ?: "---° —", R.drawable.lc_navigation_2, p.accent, t.bearing ?: 0f)
                val tripMs = t.tripStartAt?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
                StatCard("TRIP DURATION", TripMath.tripTime(tripMs), R.drawable.lc_clock, p.text2, onLongClick = TelemetryManager::resetTrip)
                StatCard("TRIP DISTANCE", TripMath.distance(t.distanceM).lowercase(Locale.ROOT), R.drawable.lc_route, p.text2,
                    onLongClick = TelemetryManager::resetTrip)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().topHairline(p.border).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MapAction(R.drawable.lc_map, Rv.Green, "Full Map", { open(context, Shortcuts.MAPS) }) {
                LIcon(R.drawable.lc_arrow_up_right, 20, tint = p.text2)
            }
            MapAction(R.drawable.lc_columns_2, p.text, "Split-Screen", {
                if (!NativeAppLauncher.splitScreen(context)) pick(Shortcuts.MAPS)
            }) {
                Box(Modifier.rCard(p.card, 999, p, border = false).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Txt("50 / 50", 15, Sans, color = p.text2, weight = FontWeight.Bold)
                }
            }
        }
    }
}

/** "015° North": degrees first, then the word, as Rivian writes it. */
private fun headingWords(bearing: Float): String {
    val deg = ((bearing % 360f) + 360f) % 360f
    val words = arrayOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest")
    return String.format(Locale.ROOT, "%03d° %s", deg.toInt(), words[((deg + 22.5f) / 45f).toInt() % 8])
}

/** Turn-by-turn on forest green, the same by day and night; yellow on it is 4.7:1. */
@Composable
private fun NavCard(nav: NavInfo, onClick: () -> Unit) {
    val arrow by NavHub.arrow.collectAsState()
    Row(
        Modifier.fillMaxWidth().height(116.dp).touch(onClick = onClick).surface(Rv.NavCard, 22, true, Rv.Green).padding(18.dp),
        Arrangement.spacedBy(18.dp), Alignment.CenterVertically,
    ) {
        Box(Modifier.size(80.dp).surface(Rv.NavBox, 18, true, Rv.Green.copy(alpha = 0.5f)), Alignment.Center) {
            val a = arrow
            if (a != null) Image(a.asImageBitmap(), null, Modifier.size(52.dp), colorFilter = ColorFilter.tint(Rv.Yellow))
            else LIcon(
                if (nav.maneuver?.contains("LEFT") == true) R.drawable.lc_corner_up_left else R.drawable.lc_navigation_2,
                44, tint = Rv.Yellow,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(nav.distance?.let(::spacedDistance) ?: "--", 36, Sans, Modifier.alignByBaseline(), Color.White,
                    FontWeight.ExtraBold, Track.TIGHT, tight = true)
                nav.maneuver?.let {
                    Txt("• $it", 18, Sans, Modifier.alignByBaseline(), Rv.Yellow, FontWeight.Bold, Track.WIDER)
                }
            }
            Txt(nav.road.orEmpty(), 20, Sans, Modifier.padding(top = 6.dp), Rv.NavRoad, FontWeight.SemiBold)
        }
        Column(
            Modifier.surface(Rv.NavBox, 16, border = false).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt("ARRIVAL", 15, Sans, color = Rv.NavLabel, weight = FontWeight.Bold, tracking = Track.WIDEST)
            Txt(nav.etaMin?.let { "$it min" } ?: "-- min", 20, Sans, Modifier.padding(top = 2.dp), Color.White, FontWeight.ExtraBold)
        }
    }
}

/** "250m" -> "250 m", the way the Rivian card spaces it. */
private fun spacedDistance(d: String) = d.replace(Regex("""^([\d.]+)(\D+)$"""), "$1 $2")

@Composable
private fun Cruising(fix: Boolean, onClick: () -> Unit) {
    val p = LocalR.current
    val w by WeatherRepo.state.collectAsState()
    Row(
        Modifier.fillMaxWidth().height(116.dp).touch(onClick = onClick).rCard(p.elevated, 22, p).padding(20.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(end = 16.dp), Arrangement.spacedBy(16.dp), Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).rCard(p.card, 18, p), Alignment.Center) { LIcon(R.drawable.lc_compass, 36, tint = p.accent) }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (fix) Rv.Green else p.muted, CircleShape))
                    Txt(if (fix) "CRUISING • GPS LOCKED" else "CRUISING • NO GPS", 15, Sans, color = p.text2,
                        weight = FontWeight.Bold, tracking = Track.WIDER)
                }
                Txt(w.place, 24, Sans, Modifier.padding(top = 4.dp), p.text, FontWeight.ExtraBold)
            }
        }
        Column(Modifier.rCard(p.card, 16, p).padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
            Txt(w.tempC?.let { "$it°C" } ?: "--°C", 26, Sans, color = p.text, weight = FontWeight.ExtraBold)
            Txt(conditionWords(w.condition), 15, Sans, color = p.text2, weight = FontWeight.SemiBold)
        }
    }
}

private fun conditionWords(c: String) = when (c) {
    "CLEAR" -> "Clear Sky"
    "CLOUDY" -> "Cloudy"
    "FOG" -> "Fog"
    "DRIZZLE" -> "Drizzle"
    "RAIN" -> "Rain"
    "SNOW" -> "Snow"
    "STORM" -> "Thunderstorm"
    "" -> "—"
    else -> "Fair"
}

@Composable
private fun StatCard(label: String, value: String, icon: Int, tint: Color, rotation: Float = 0f, onLongClick: (() -> Unit)? = null) {
    val p = LocalR.current
    var mod = Modifier.fillMaxWidth().layer()
    if (onLongClick != null) mod = mod.touch(onLongClick = onLongClick) {}
    Row(mod.rCard(p.elevated, 20, p).padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(label, 15, Sans, color = p.text2, weight = FontWeight.Bold, tracking = Track.WIDER)
            Txt(value, 20, Sans, Modifier.padding(top = 2.dp), p.text, FontWeight.ExtraBold)
        }
        Box(Modifier.size(42.dp).rCard(p.card, 999, p), Alignment.Center) { LIcon(icon, 20, Modifier.rotate(rotation), tint) }
    }
}

@Composable
private fun RowScope.MapAction(icon: Int, tint: Color, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val p = LocalR.current
    Row(
        Modifier.weight(1f).height(72.dp).touch(onClick = onClick).rCard(p.elevated, 20, p).padding(horizontal = 22.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LIcon(icon, 24, tint = tint)
            Txt(label, 18, Sans, color = p.text, weight = FontWeight.ExtraBold)
        }
        trailing()
    }
}

// ---- Zone B: CarPlay, media, quick apps ---------------------------------------------------------

@Composable
private fun MediaZone() {
    val p = LocalR.current
    val context = LocalContext.current
    Column(Modifier.width(460.dp).fillMaxHeight().layer(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth().height(88.dp)
                .touch(onLongClick = { pick(Shortcuts.ZLINK) }) { open(context, Shortcuts.ZLINK) }
                .rCard(p.card, 26, p).padding(horizontal = 20.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).padding(end = 12.dp), Arrangement.spacedBy(16.dp), Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).surface(Rv.NavCard, 18, border = false), Alignment.Center) {
                    LIcon(R.drawable.lc_play_filled, 26, tint = Color.White)
                }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Apple CarPlay", 20, Sans, color = p.text, weight = FontWeight.ExtraBold)
                        Box(Modifier.rCard(p.elevated, 999, p).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Txt("ZLINK", 15, Sans, color = p.text2, weight = FontWeight.ExtraBold)
                        }
                    }
                    Row(Modifier.padding(top = 4.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                        // Static: an endless pulse would redraw the screen every frame.
                        Box(Modifier.size(8.dp).background(Rv.Green, CircleShape))
                        // Whether a phone is linked is Zlink's to know, not the launcher's.
                        Txt("Tap to open fullscreen", 15, Sans, color = p.text2, weight = FontWeight.SemiBold)
                    }
                }
            }
            Box(Modifier.size(40.dp).background(p.elevated, CircleShape), Alignment.Center) {
                LIcon(R.drawable.lc_chevron_right, 22, tint = p.text)
            }
        }
        MediaCard()
        Row(Modifier.fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SlotTile(Shortcuts.SLOT)
            Tile("DSP / EQ", R.drawable.lc_sliders_horizontal, { pick(Shortcuts.DSP) }) { open(context, Shortcuts.DSP) }
            Tile("YouTube", R.drawable.lc_tv, { pick(Shortcuts.YOUTUBE) }) { open(context, Shortcuts.YOUTUBE) }
            Tile("Display", R.drawable.lc_sun_moon, null) { Ui.overlay = Overlay.QuickSettings }
        }
    }
}

@Composable
private fun MediaCard() {
    val p = LocalR.current
    val context = LocalContext.current
    val source by MediaHub.source.collectAsState()
    val info by MediaHub.info.collectAsState()
    val listening by CarNotificationListener.connected.collectAsState()
    Column(Modifier.fillMaxWidth().height(404.dp).rCard(p.card, 28, p).padding(18.dp)) {
        // Segmented source control on its own tray.
        Row(
            Modifier.fillMaxWidth().height(54.dp).rCard(p.elevated, 18, p).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Segment("YT Music", source == MediaSource.YT) { MediaHub.select(MediaSource.YT) }
            Segment("Bluetooth", source == MediaSource.BT) { MediaHub.select(MediaSource.BT) }
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .touch(onLongClick = { pick(Shortcuts.RADIO) }) { open(context, Shortcuts.RADIO) }
                    .rCard(p.pill, 13, p, border = false),
                Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), Alignment.CenterVertically,
            ) {
                LIcon(R.drawable.lc_radio, 16, tint = p.text)
                Txt("Radio", 15, Sans, color = p.text, weight = FontWeight.Bold)
                LIcon(R.drawable.lc_arrow_up_right, 14, tint = p.text2)
            }
        }
        Spacer(Modifier.weight(1f))
        val sourceLabel = when {
            source == MediaSource.BT -> "BLUETOOTH AUDIO"
            info == null || info?.packageName == MediaHub.YT_MUSIC -> "YOUTUBE MUSIC"
            else -> appLabel(context, info!!.packageName).uppercase(Locale.ROOT)
        }
        val m = info
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                .then(if (!listening) Modifier.touch { openListenerSettings(context) } else Modifier),
            Arrangement.spacedBy(18.dp), Alignment.CenterVertically,
        ) {
            Box(Modifier.size(116.dp).rCard(p.elevated, 22, p), Alignment.Center) {
                val art = m?.art
                if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else LIcon(R.drawable.lc_disc_3, 52, tint = p.accent)
            }
            Column(Modifier.weight(1f)) {
                Txt(sourceLabel, 15, Sans, color = p.accent, weight = FontWeight.ExtraBold, tracking = Track.WIDER)
                Txt(m?.title?.ifBlank { null } ?: "Nothing playing", 26, Sans, Modifier.padding(top = 4.dp), p.text, FontWeight.ExtraBold)
                Txt(
                    when {
                        m != null -> m.artist
                        !listening -> "Tap to allow notification access"
                        source == MediaSource.YT -> "Press play to open YT Music"
                        else -> "Play music on your phone"
                    },
                    17, Sans, Modifier.padding(top = 2.dp), p.text2, FontWeight.SemiBold,
                )
                Progress(m, Modifier.padding(top = 18.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(128.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().touch { MediaHub.previous() }.rCard(p.elevated, 20, p), Alignment.Center) {
                LIcon(R.drawable.lc_skip_back_filled, 36, tint = p.text)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().touch {
                    when {
                        MediaHub.hasSession -> MediaHub.playPause()
                        source == MediaSource.YT -> open(context, Shortcuts.YT_MUSIC)
                        NativeAppLauncher.openBluetoothMusic(context) -> Unit
                        else -> Ui.toast("No Bluetooth audio. Play on your phone.")
                    }
                }.surface(Rv.Yellow, 20, border = false),
                Alignment.Center,
            ) {
                LIcon(if (m?.playing == true) R.drawable.lc_pause_filled else R.drawable.lc_play_filled, 44, tint = Rv.OnYellow)
            }
            Box(Modifier.weight(1f).fillMaxHeight().touch { MediaHub.next() }.rCard(p.elevated, 20, p), Alignment.Center) {
                LIcon(R.drawable.lc_skip_forward_filled, 36, tint = p.text)
            }
        }
    }
}

@Composable
private fun RowScope.Segment(label: String, active: Boolean, onClick: () -> Unit) {
    val p = LocalR.current
    Row(
        Modifier.weight(1f).fillMaxHeight().touch(onClick = onClick)
            .then(if (active) Modifier.rCard(p.card, 13, p, border = false) else Modifier),
        Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), Alignment.CenterVertically,
    ) {
        if (active) Box(Modifier.size(7.dp).background(p.accent, CircleShape))
        Txt(label, 15, Sans, color = if (active) p.text else p.text2, weight = if (active) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

@Composable
private fun Progress(info: MediaInfo?, modifier: Modifier) {
    val p = LocalR.current
    rememberNow()
    val duration = info?.durationMs ?: 0L
    val position = info?.positionNow() ?: 0L
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth().layer()) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(p.elevated)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(p.accent, CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.SpaceBetween) {
            Txt(clock(position), 15, Mono, color = p.text2, weight = FontWeight.Bold)
            Txt(clock(duration), 15, Mono, color = p.text2, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RowScope.Tile(label: String, icon: Int, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    TileFrame(label, { LIcon(icon, 26, tint = LocalR.current.text) }, onLongClick, onClick)
}

/** The first quick tile: the radio until the driver holds it and picks another app. */
@Composable
private fun RowScope.SlotTile(s: Shortcut) {
    val context = LocalContext.current
    val version = Ui.appsVersion
    val app = remember(version) { runCatching { NativeAppLauncher.savedApp(context, s) }.getOrNull() }
    val icon = app?.icon
    TileFrame(
        app?.label ?: s.label,
        { if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(32.dp)) else LIcon(s.icon, 26, tint = LocalR.current.text) },
        { pick(s) },
    ) { open(context, s) }
}

@Composable
private fun RowScope.TileFrame(label: String, icon: @Composable () -> Unit, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val p = LocalR.current
    Column(
        Modifier.weight(1f).fillMaxHeight().touch(onLongClick, onClick).rCard(p.card, 24, p),
        Arrangement.spacedBy(12.dp, Alignment.CenterVertically), Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).rCard(p.elevated, 18, p, border = false), Alignment.Center) { icon() }
        Txt(label, 15, Sans, Modifier.padding(horizontal = 8.dp), p.text, FontWeight.ExtraBold)
    }
}

// ---- Zone C: driver dock ------------------------------------------------------------------------

@Composable
private fun DriverDock() {
    val p = LocalR.current
    val now = rememberNow(60_000)
    val time = remember(now / 60_000) { SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(now)) }
    Column(
        Modifier.width(104.dp).fillMaxHeight().layer().rCard(p.card, 28, p).padding(horizontal = 12.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(time, 24, Sans, Modifier.layer(), color = p.text, weight = FontWeight.ExtraBold, tight = true)
            // Tap to cycle AUTO -> DAY -> NIGHT.
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().touch { Look.cycleMode() }.rCard(p.elevated, 999, p)
                    .padding(vertical = 5.dp),
                Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), Alignment.CenterVertically,
            ) {
                LIcon(
                    when (Look.mode) {
                        ThemeMode.AUTO -> R.drawable.lc_car_front
                        ThemeMode.DAY -> R.drawable.lc_sun
                        ThemeMode.NIGHT -> R.drawable.lc_moon
                    },
                    15, tint = p.accent,
                )
                Txt(Look.mode.name, 15, Sans, color = p.text2, weight = FontWeight.ExtraBold)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(74.dp).touch { Ui.overlay = Overlay.None }.surface(Rv.Yellow, 22, border = false), Alignment.Center) {
                LIcon(R.drawable.lc_compass, 32, tint = Rv.OnYellow)
            }
            DockButton(R.drawable.lc_layout_grid) { Ui.overlay = Overlay.Drawer }
            DockButton(R.drawable.lc_sliders_vertical) { Ui.overlay = Overlay.QuickSettings }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Column(
            Modifier.padding(top = 14.dp).size(74.dp).touch { Ui.overlay = Overlay.Ambient }.rCard(p.elevated, 22, p),
            Arrangement.spacedBy(4.dp, Alignment.CenterVertically), Alignment.CenterHorizontally,
        ) {
            LIcon(R.drawable.lc_moon, 24, tint = p.accent)
            Txt("CALM", 15, Sans, color = p.text2, weight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun DockButton(icon: Int, onClick: () -> Unit) {
    val p = LocalR.current
    Box(Modifier.size(74.dp).touch(onClick = onClick).rCard(p.elevated, 22, p), Alignment.Center) {
        LIcon(icon, 30, tint = p.text)
    }
}

/** Dark pill with a yellow hairline; the same by day and night. */
@Composable
private fun RivianToast(modifier: Modifier) {
    val message = Ui.toast ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2400)
        Ui.toast = null
    }
    Row(
        modifier.widthIn(max = 900.dp).surface(Rv.OnYellow, 18, true, Rv.Yellow).padding(horizontal = 22.dp, vertical = 14.dp),
        Arrangement.spacedBy(10.dp), Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Rv.Yellow, CircleShape))
        Txt(message, 15, Sans, Modifier.weight(1f, fill = false), Color(0xFFF3F5F6), FontWeight.ExtraBold)
    }
}
