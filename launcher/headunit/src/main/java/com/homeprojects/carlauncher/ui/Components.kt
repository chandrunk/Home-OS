@file:OptIn(ExperimentalFoundationApi::class)

package com.homeprojects.carlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homeprojects.carlauncher.Shortcut
import com.homeprojects.carlauncher.ui.theme.N
import com.homeprojects.carlauncher.ui.theme.Sans
import kotlinx.coroutines.delay

sealed interface Overlay {
    data object None : Overlay
    data object Drawer : Overlay
    data object QuickSettings : Overlay
    data object Ambient : Overlay
    /** Choose which installed app a shortcut opens. */
    data class Picker(val shortcut: Shortcut) : Overlay
}

/** Screen-wide UI state. One activity, one screen: a plain object is enough. */
object Ui {
    var overlay by mutableStateOf<Overlay>(Overlay.None)
    var toast by mutableStateOf<String?>(null)
    var online by mutableStateOf(false)
    var wifi by mutableStateOf(false)
    /** Bumped when apps may have changed (resume, a shortcut re-assigned): shortcuts resolve again. */
    var appsVersion by mutableIntStateOf(0)

    fun toast(message: String) {
        toast = message
    }
}

/** Monochrome: app icons and pictures in greyscale, like the rest of the screen. */
val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Tailwind's tracking-* in em. */
object Track {
    const val TIGHTER = -0.05f
    const val TIGHT = -0.025f
    const val WIDE = 0.025f
    const val WIDER = 0.05f
    const val WIDEST = 0.1f
}

/**
 * One line of text. Never wraps: anything longer than its slot ends in an ellipsis, so a long song
 * title or road name can't push the layout around. Nothing on the screen is smaller than 15 px.
 */
@Composable
fun Txt(
    text: String,
    size: Int,
    family: FontFamily,
    modifier: Modifier = Modifier,
    color: Color = N.White,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
    /** Tailwind leading-none for the big dot-matrix numbers. */
    tight: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = family,
            fontWeight = weight,
            letterSpacing = tracking.em,
            lineHeight = if (tight) 1.em else 1.3.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            // No ligatures, as browsers do with letter-spacing: JetBrains Mono would join "///" into one glyph.
            fontFeatureSettings = "liga 0, calt 0",
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RedDot(size: Int, modifier: Modifier = Modifier, color: Color = N.Red) {
    Box(modifier.size(size.dp).background(color, CircleShape))
}

/** A Lucide icon (res/drawable/lc_*), tinted. */
@Composable
fun LIcon(res: Int, size: Int, modifier: Modifier = Modifier, tint: Color = N.White) {
    Image(painterResource(res), null, modifier.size(size.dp), colorFilter = ColorFilter.tint(tint))
}

/** Matte surface: rounded, flat colour, 1 dp hairline. No shadows (zero-blur policy). */
fun Modifier.surface(color: Color, radius: Int, border: Boolean = true): Modifier {
    val shape = if (radius >= 999) CircleShape else RoundedCornerShape(radius.dp)
    return clip(shape).background(color).then(if (border) Modifier.border(1.dp, N.Border, shape) else Modifier)
}

/** The design's .touch-card: shrinks to 97% while pressed, 75 ms, no ripple. */
fun Modifier.touch(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(75), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/** .bg-dot-matrix: a 1.25 px dot centred in every 20 px cell. */
fun Modifier.dotMatrix(): Modifier = drawBehind {
    val step = 20.dp.toPx()
    val r = 1.25.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(N.Border, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

fun Modifier.topHairline(): Modifier = drawBehind {
    drawLine(N.Border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

fun Modifier.bottomHairline(): Modifier = drawBehind {
    drawLine(N.Border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

/** Wall-clock time, ticking on the second. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val message = Ui.toast ?: return
    LaunchedEffect(message) {
        delay(2400)
        Ui.toast = null
    }
    Row(
        modifier.widthIn(max = 900.dp).surface(N.White, 999, border = false).padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedDot(8, color = N.Black)
        Txt(message, 15, Sans, Modifier.weight(1f, fill = false), N.Black, FontWeight.ExtraBold)
    }
}
