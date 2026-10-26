package com.homeprojects.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homeprojects.carlauncher.CarNotificationListener
import com.homeprojects.carlauncher.Connectivity
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
import com.homeprojects.carlauncher.ui.theme.DESIGN_H
import com.homeprojects.carlauncher.ui.theme.DESIGN_W
import com.homeprojects.carlauncher.ui.theme.Dot
import com.homeprojects.carlauncher.ui.theme.Mono
import com.homeprojects.carlauncher.ui.theme.N
import com.homeprojects.carlauncher.ui.theme.Night
import com.homeprojects.carlauncher.ui.theme.Sans
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The whole 1280 x 720 screen: telemetry canvas, media hub, right-hand driver dock, overlays. */
@Composable
fun HomeScreen() {
    Box(Modifier.size(DESIGN_W.dp, DESIGN_H.dp).background(N.Black)) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryZone()
            MediaZone()
            DriverDock()
        }
        when (val o = Ui.overlay) {
            Overlay.None -> Unit
            Overlay.Drawer -> AppDrawer()
            Overlay.QuickSettings -> QuickSettings()
            Overlay.Ambient -> AmbientClock()
            is Overlay.Picker -> AppPicker(o.shortcut)
        }
        ToastHost(Modifier.align(Alignment.BottomStart).padding(24.dp))
    }
}

/** Opens a shortcut's app; when none is found, asks the driver to pick one. */
fun open(context: Context, s: Shortcut) {
    if (!NativeAppLauncher.launch(context, s)) {
        Ui.toast("${s.label.uppercase(Locale.ROOT)} NOT FOUND // PICK AN APP")
        Ui.overlay = Overlay.Picker(s)
    }
}

fun pick(s: Shortcut) {
    Ui.overlay = Overlay.Picker(s)
}

// ---- Zone A: telemetry & navigation canvas ------------------------------------------------------

@Composable
private fun TelemetryZone() {
    val context = LocalContext.current
    val t by TelemetryManager.state.collectAsState()
    rememberNow()   // trip time and the GPS-fix state tick every second
    val fix = t.hasFix()
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.width(664.dp).fillMaxHeight().layer().clip(shape).background(N.Card).dotMatrix()
            .border(1.dp, N.Border, shape).padding(22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RedDot(8, color = N.Muted)
                Txt("NOTHING CAR OS", 15, Mono, color = N.Muted, tracking = Track.WIDEST)
            }
            // Which network and which phone, where the eye already goes for status. Long names end in "…".
            Row(Modifier.weight(1f), Arrangement.spacedBy(8.dp, Alignment.End), Alignment.CenterVertically) {
                StatusChip(
                    if (Connectivity.ssid != null || Ui.wifi) R.drawable.lc_wifi else R.drawable.lc_wifi_off,
                    Connectivity.ssid ?: if (Ui.wifi) "Wi-Fi" else "No Wi-Fi",
                    on = Ui.online,
                )
                StatusChip(
                    R.drawable.lc_bluetooth,
                    Connectivity.phone ?: if (Connectivity.bluetoothOn) "No phone" else "BT off",
                    on = Connectivity.phone != null,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val nav by NavHub.state.collectAsState()
        val n = nav
        if (n != null) ActiveNav(n) { open(context, Shortcuts.MAPS) }
        else Cruising(fix) { open(context, Shortcuts.MAPS) }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Column {
                // The number and its unit say what this is; only the GPS state needs a label.
                Box(Modifier.padding(bottom = 4.dp).surface(N.Elevated, 4).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Txt(if (fix) "GPS" else "NO FIX", 15, Mono, color = if (fix) N.White else N.Dim)
                }
                // Dot-matrix only where it is big enough to read at a glance: the speed, the temperature.
                Txt(if (fix) "${t.speedKmh ?: 0}" else "--", 180, Dot, Modifier.layer(), tight = true)
                Txt("KM / H ///", 16, Mono, Modifier.padding(top = 8.dp), N.Red, FontWeight.Bold, 0.3f)
            }
            Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("HEADING", t.bearing?.let(TripMath::heading) ?: "-- / ---°") {
                    Box(Modifier.size(36.dp).background(N.Elevated, CircleShape), Alignment.Center) {
                        LIcon(R.drawable.lc_navigation_2, 18, Modifier.rotate(t.bearing ?: 0f), N.White)
                    }
                }
                val tripMs = t.tripStartAt?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
                StatCard("TRIP TIME", TripMath.tripTime(tripMs), onLongClick = TelemetryManager::resetTrip) {
                    LIcon(R.drawable.lc_clock, 20, tint = N.Muted)
                }
                StatCard("DISTANCE", TripMath.distance(t.distanceM), onLongClick = TelemetryManager::resetTrip) {
                    LIcon(R.drawable.lc_route, 20, tint = N.Muted)
                }
            }
        }
        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth().dottedLine(N.Border).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapAction(R.drawable.lc_map, N.White, "Open Maps", { open(context, Shortcuts.MAPS) }) {
                LIcon(R.drawable.lc_arrow_up_right, 20, tint = N.Muted)
            }
            MapAction(R.drawable.lc_columns_2, N.White, "Split-Screen", {
                if (!NativeAppLauncher.splitScreen(context)) pick(Shortcuts.MAPS)
            }) {}
        }
    }
}

@Composable
private fun ActiveNav(nav: NavInfo, onClick: () -> Unit) {
    val arrow by NavHub.arrow.collectAsState()
    // White by day; at night a dark card, so the brightest thing in the cabin isn't the screen.
    val night = Night.on
    Row(
        Modifier.fillMaxWidth().height(116.dp).touch(onClick = onClick)
            .surface(if (night) N.Elevated else N.White, 20, border = night).padding(16.dp),
        Arrangement.spacedBy(16.dp), Alignment.CenterVertically,
    ) {
        Box(Modifier.size(84.dp).background(N.Black, RoundedCornerShape(16.dp)), Alignment.Center) {
            val a = arrow
            if (a != null) Image(a.asImageBitmap(), null, Modifier.size(56.dp), colorFilter = ColorFilter.tint(N.White))
            else LIcon(
                if (nav.maneuver?.contains("LEFT") == true) R.drawable.lc_corner_up_left else R.drawable.lc_navigation_2,
                44,
            )
        }
        // Takes what the arrow and the ETA leave, so a long road name ends in "…" instead of
        // pushing the ETA off the card.
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt(nav.distance ?: "--", 36, Sans, Modifier.alignByBaseline(), if (night) N.White else N.Black,
                    FontWeight.ExtraBold, Track.TIGHT, tight = true)
                nav.maneuver?.let {
                    Txt("• $it", 19, Sans, Modifier.alignByBaseline(), N.Red, FontWeight.ExtraBold, Track.WIDE)
                }
            }
            Txt(nav.road.orEmpty(), 20, Sans, Modifier.padding(top = 6.dp), if (night) N.Muted else N.Ink, FontWeight.Bold)
        }
        Column(
            Modifier.padding(end = 8.dp).surface(N.Black, 14, border = false).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt("ETA", 15, Mono, color = N.Muted, weight = FontWeight.Bold, tracking = Track.WIDEST)
            Txt(nav.etaMin?.let { "$it MIN" } ?: "-- MIN", 20, Mono, Modifier.padding(top = 2.dp), weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Cruising(fix: Boolean, onClick: () -> Unit) {
    val w by WeatherRepo.state.collectAsState()
    Row(
        Modifier.fillMaxWidth().height(116.dp).touch(onClick = onClick).surface(N.Elevated, 20).padding(20.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).padding(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(72.dp).surface(N.Card, 16), Alignment.Center) {
                LIcon(R.drawable.lc_compass, 36)
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RedDot(7, color = N.Muted)
                    Txt(if (fix) "CRUISING MODE / GPS LOCKED" else "CRUISING MODE / NO GPS", 15, Mono,
                        color = N.Muted, weight = FontWeight.Bold, tracking = Track.WIDEST)
                }
                Txt(w.place, 24, Sans, Modifier.padding(top = 4.dp), weight = FontWeight.ExtraBold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Txt(w.tempC?.let { "$it°C" } ?: "--°C", 44, Dot, tight = true)
            Txt(w.label, 15, Mono, color = N.Muted)
        }
    }
}

/** A status pill: icon and a name, dimmed when not connected. */
@Composable
private fun RowScope.StatusChip(icon: Int, text: String, on: Boolean) {
    Row(
        Modifier.weight(1f, fill = false).surface(N.Elevated, 999).padding(horizontal = 10.dp, vertical = 4.dp),
        Arrangement.spacedBy(6.dp), Alignment.CenterVertically,
    ) {
        LIcon(icon, 16, tint = if (on) N.White else N.Muted)
        Txt(text, 15, Mono, color = if (on) N.White else N.Muted, weight = FontWeight.Bold)
    }
}

@Composable
private fun StatCard(label: String, value: String, onLongClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    var mod = Modifier.fillMaxWidth().layer()
    if (onLongClick != null) mod = mod.touch(onLongClick = onLongClick) {}
    Row(
        mod.surface(N.Black.copy(alpha = 0.9f), 16).padding(14.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, 15, Mono, color = N.Muted)
            Txt(value, 18, Mono, Modifier.padding(top = 2.dp), weight = FontWeight.Bold)
        }
        trailing()
    }
}

@Composable
private fun RowScope.MapAction(icon: Int, tint: androidx.compose.ui.graphics.Color, label: String,
                               onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.weight(1f).height(72.dp).touch(onClick = onClick).surface(N.Elevated, 16).padding(horizontal = 20.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LIcon(icon, 24, tint = tint)
            Txt(label, 18, Sans, weight = FontWeight.Bold)
        }
        trailing()
    }
}

// ---- Zone B: CarPlay pill, media hub, quick apps -------------------------------------------------

@Composable
private fun MediaZone() {
    val context = LocalContext.current
    Column(Modifier.width(460.dp).fillMaxHeight().layer(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(88.dp)
                .touch(onLongClick = { pick(Shortcuts.ZLINK) }) { open(context, Shortcuts.ZLINK) }
                .surface(N.Card, 22).padding(horizontal = 20.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).background(N.White, CircleShape), Alignment.Center) {
                    LIcon(R.drawable.lc_play_filled, 24, tint = N.Black)
                }
                Column {
                    Txt("CARPLAY / ANDROID AUTO", 17, Mono, weight = FontWeight.Bold, tracking = Track.WIDE)
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PulsingDot()
                        Txt("PHONE LINK • TAP TO RESUME", 15, Mono, color = N.Muted)
                    }
                }
            }
            Box(Modifier.size(40.dp).background(N.Elevated, CircleShape), Alignment.Center) {
                LIcon(R.drawable.lc_chevron_right, 22)
            }
        }
        MediaHubCard()
        Row(Modifier.fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SlotTile(Shortcuts.SLOT)
            QuickApp(Shortcuts.DSP, "DSP / EQ", R.drawable.lc_sliders_horizontal, N.White)
            QuickApp(Shortcuts.YOUTUBE, "YouTube", R.drawable.lc_play, N.White)
            QuickTile("Controls", { LIcon(R.drawable.lc_settings_2, 26) }) { Ui.overlay = Overlay.QuickSettings }
        }
    }
}

// Static, not pulsing: an endless animation redraws the whole screen every frame (a full CPU core on
// a weak head unit), and blinking things pull the driver's eye.
@Composable
private fun PulsingDot() = RedDot(8, color = N.Muted)

@Composable
private fun MediaHubCard() {
    val context = LocalContext.current
    val source by MediaHub.source.collectAsState()
    val info by MediaHub.info.collectAsState()
    val listening by CarNotificationListener.connected.collectAsState()
    Column(Modifier.fillMaxWidth().height(408.dp).surface(N.Card, 24).padding(18.dp)) {
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceTab("YT Music", R.drawable.lc_disc_3, source == MediaSource.YT) { MediaHub.select(MediaSource.YT) }
            SourceTab("Bluetooth", R.drawable.lc_bluetooth, source == MediaSource.BT) { MediaHub.select(MediaSource.BT) }
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .touch(onLongClick = { pick(Shortcuts.RADIO) }) { open(context, Shortcuts.RADIO) }
                    .surface(N.Black, 14),
                Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), Alignment.CenterVertically,
            ) {
                LIcon(R.drawable.lc_radio, 18)
                Txt("Radio", 15, Sans, weight = FontWeight.Bold)
                LIcon(R.drawable.lc_arrow_up_right, 14, tint = N.Muted)
            }
        }
        Spacer(Modifier.weight(1f))
        NowPlaying(info, source, listening)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).fillMaxHeight().touch { MediaHub.previous() }.surface(N.Elevated, 18),
                Alignment.Center,
            ) { LIcon(R.drawable.lc_skip_back_filled, 36) }
            Box(
                Modifier.weight(1f).fillMaxHeight().touch {
                    when {
                        MediaHub.hasSession -> MediaHub.playPause()
                        source == MediaSource.YT -> if (!MediaHub.resume(context)) open(context, Shortcuts.YT_MUSIC)
                        NativeAppLauncher.openBluetoothMusic(context) -> Unit
                        else -> Ui.toast("NO BLUETOOTH AUDIO // PLAY ON YOUR PHONE")
                    }
                }.surface(N.White, 18, border = false),
                Alignment.Center,
            ) {
                LIcon(if (info?.playing == true) R.drawable.lc_pause_filled else R.drawable.lc_play_filled, 44, tint = N.Black)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().touch { MediaHub.next() }.surface(N.Elevated, 18),
                Alignment.Center,
            ) { LIcon(R.drawable.lc_skip_forward_filled, 36) }
        }
    }
}

@Composable
private fun RowScope.SourceTab(label: String, icon: Int, active: Boolean, onClick: () -> Unit) {
    val fg = if (active) N.Black else N.Muted
    Box(
        Modifier.weight(1f).fillMaxHeight().touch(onClick = onClick)
            .surface(if (active) N.White else N.Elevated, 14, border = !active),
        Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LIcon(icon, 18, tint = fg)
            Txt(label, 15, Sans, color = fg, weight = if (active) FontWeight.ExtraBold else FontWeight.Bold)
        }
    }
}

@Composable
private fun NowPlaying(info: MediaInfo?, source: MediaSource, listening: Boolean) {
    val context = LocalContext.current
    val sourceLabel = when {
        source == MediaSource.BT -> "BLUETOOTH A2DP // PHONE AUDIO"
        info == null || info.packageName == MediaHub.YT_MUSIC -> "YOUTUBE MUSIC // STREAM"
        else -> appLabel(context, info.packageName).uppercase(Locale.ROOT) + " // STREAM"
    }
    val title = info?.title?.ifBlank { null } ?: "Nothing playing"
    val subtitle = when {
        info != null -> info.artist
        !listening -> "Tap to allow notification access"
        source == MediaSource.YT -> "Press play to open YT Music"
        else -> "Play music on your phone"
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .then(if (!listening) Modifier.touch { openListenerSettings(context) } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(116.dp).surface(N.Elevated, 20), Alignment.Center) {
            val art = info?.art
            if (art != null) {
                Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(44.dp).border(2.dp, N.Muted, CircleShape), Alignment.Center) { RedDot(12, color = N.Muted) }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RedDot(6, color = N.Muted)
                Txt(sourceLabel, 15, Mono, color = N.Muted, weight = FontWeight.Bold, tracking = Track.WIDEST)
            }
            Txt(title, 26, Sans, Modifier.padding(top = 6.dp), weight = FontWeight.ExtraBold)
            Txt(subtitle, 17, Sans, Modifier.padding(top = 2.dp), N.Muted, FontWeight.SemiBold)
            // Bluetooth without a timeline (NWD's module sends none): no bar rather than one stuck at 00:00.
            if (source != MediaSource.BT || (info?.durationMs ?: 0L) > 0) Progress(info, Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun Progress(info: MediaInfo?, modifier: Modifier) {
    rememberNow()   // tick
    val duration = info?.durationMs ?: 0L
    val position = info?.positionNow() ?: 0L
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth().layer()) {
        DotProgress(fraction, N.White, N.Elevated, N.Red, Modifier.fillMaxWidth().padding(vertical = 3.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween) {
            Txt(clock(position), 15, Mono, color = N.Muted)
            Txt(clock(duration), 15, Mono, color = N.Muted)
        }
    }
}

internal fun clock(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
}

internal fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg.substringAfterLast('.'))

fun openListenerSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Ui.toast("RUN setup_adb.sh TO GRANT NOTIFICATION ACCESS") }
}

@Composable
private fun RowScope.QuickApp(s: Shortcut, label: String, icon: Int, tint: androidx.compose.ui.graphics.Color) {
    val context = LocalContext.current
    QuickTile(label, { LIcon(icon, 26, tint = tint) }, onLongClick = { pick(s) }) { open(context, s) }
}

/** A tile the driver can re-assign: hold it and pick an app; it then shows that app's name and icon. */
@Composable
private fun RowScope.SlotTile(s: Shortcut) {
    val context = LocalContext.current
    val version = Ui.appsVersion
    val app = remember(version) { runCatching { NativeAppLauncher.savedApp(context, s) }.getOrNull() }
    val icon = app?.icon
    QuickTile(
        app?.label ?: s.label,
        { if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(30.dp), colorFilter = Greyscale)
          else LIcon(s.icon, 26) },
        onLongClick = { pick(s) },
    ) { open(context, s) }
}

@Composable
private fun RowScope.QuickTile(label: String, icon: @Composable () -> Unit,
                               onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxHeight().touch(onLongClick, onClick).surface(N.Card, 20),
        Arrangement.spacedBy(12.dp, Alignment.CenterVertically), Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).surface(N.Elevated, 999), Alignment.Center) { icon() }
        Txt(label.uppercase(Locale.ROOT), 15, Mono, Modifier.padding(horizontal = 8.dp), weight = FontWeight.Bold)
    }
}

// ---- Zone C: right-hand driver dock --------------------------------------------------------------

@Composable
private fun DriverDock() {
    val now = rememberNow(60_000)
    val time = remember(now / 60_000) { SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(now)) }
    Column(
        Modifier.width(100.dp).fillMaxHeight().layer().surface(N.Card, 24).padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().dottedLine(N.Border, top = false).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(time, 22, Mono, Modifier.layer(), weight = FontWeight.Bold, tight = true)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DockButton(R.drawable.lc_layout_dashboard, 30, Ui.overlay == Overlay.None) { Ui.overlay = Overlay.None }
            DockButton(R.drawable.lc_grip, 30, false) { Ui.overlay = Overlay.Drawer }
            DockButton(R.drawable.lc_sliders_vertical, 28, false) { Ui.overlay = Overlay.QuickSettings }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.dottedLine(N.Border).padding(top = 16.dp)) {
            Column(
                Modifier.size(72.dp).touch { Ui.overlay = Overlay.Ambient }.surface(N.Black, 20),
                Arrangement.spacedBy(4.dp, Alignment.CenterVertically), Alignment.CenterHorizontally,
            ) {
                LIcon(R.drawable.lc_moon, 24)
                Txt("AMBIENT", 15, Mono, color = N.Muted, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DockButton(icon: Int, iconSize: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).touch(onClick = onClick).surface(if (active) N.White else N.Elevated, 20, border = !active),
        Alignment.Center,
    ) {
        LIcon(icon, iconSize, tint = if (active) N.Black else N.White)
    }
}
