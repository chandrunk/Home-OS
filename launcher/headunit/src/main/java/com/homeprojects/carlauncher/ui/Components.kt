@file:OptIn(ExperimentalFoundationApi::class)

package com.homeprojects.carlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
 * Text that never grows past its slot: one line by default (or [maxLines]), and anything longer ends
 * in an ellipsis, so a long song title or road name can't push the layout around. Nothing on the
 * screen is smaller than 15 px. Digits are tabular, so a changing speed or time doesn't jitter.
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
    maxLines: Int = 1,
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
            fontFeatureSettings = "liga 0, calt 0, tnum",
        ),
        maxLines = maxLines,
        softWrap = maxLines > 1,
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
fun Modifier.surface(color: Color, radius: Int, border: Boolean = true, borderColor: Color = N.Border): Modifier {
    val shape = if (radius >= 999) CircleShape else RoundedCornerShape(radius.dp)
    return clip(shape).background(color).then(if (border) Modifier.border(1.dp, borderColor, shape) else Modifier)
}

/** The design's .touch-card: shrinks to 97% while pressed, 75 ms, no ripple. */
fun Modifier.touch(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(75), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/**
 * Its own render layer: when something inside changes (a ticking number), only this layer is
 * re-recorded instead of the whole screen. On a weak head unit that is the difference between a
 * 5 ms and a 60 ms frame.
 */
fun Modifier.layer(): Modifier = graphicsLayer()

/**
 * .bg-dot-matrix: a 1.25 px dot centred in every [cell] px cell. Drawn once into a bitmap per size and
 * colour, then reused: redrawing ~1,100 circles on every frame kept a weak head unit CPU busy.
 */
fun Modifier.dotMatrix(color: Color = N.Border, cell: Int = 20): Modifier = drawWithCache {
    val bitmap = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val paint = androidx.compose.ui.graphics.Paint().apply { this.color = color; isAntiAlias = true }
    val step = cell.dp.toPx()
    val r = 1.25.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            canvas.drawCircle(Offset(x, y), r, paint)
            x += step
        }
        y += step
    }
    onDrawBehind { drawImage(bitmap) }
}

fun Modifier.topHairline(color: Color = N.Border): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

fun Modifier.bottomHairline(color: Color = N.Border): Modifier = drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

/**
 * Wall-clock time, ticking every [periodMs] on the boundary (the second, or the minute for clocks
 * without seconds: each tick redraws, and a weak head unit notices).
 */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    val now by produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
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

/** A tap-or-drag track: [fraction] 0..1 in, the new fraction out. The skins draw their own fill on top. */
fun Modifier.dragFraction(key: Any, onChange: (Float) -> Unit): Modifier =
    pointerInput(key) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(key) { detectHorizontalDragGestures { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f)) } }

/** A dotted hairline (Nothing's divider): 2 px dots every 6 px across the top or bottom edge. */
fun Modifier.dottedLine(color: Color, top: Boolean = true): Modifier = drawBehind {
    val step = 6.dp.toPx()
    val r = 1.dp.toPx()
    val y = if (top) r else size.height - r
    var x = r
    while (x < size.width) {
        drawCircle(color, r, Offset(x, y))
        x += step
    }
}

/**
 * A progress bar of dots: lit up to [fraction], the last lit dot in [head] colour. Drawn in one pass,
 * so a ticking position redraws a single small layer.
 */
@Composable
fun DotProgress(fraction: Float, lit: Color, unlit: Color, head: Color, modifier: Modifier = Modifier, dot: Int = 5, gap: Int = 5) {
    Canvas(modifier.height(dot.dp)) {
        val d = dot.dp.toPx()
        val step = d + gap.dp.toPx()
        val n = ((size.width + gap.dp.toPx()) / step).toInt().coerceAtLeast(1)
        val on = (fraction.coerceIn(0f, 1f) * n).toInt()
        for (i in 0 until n) {
            val c = when {
                i == on && fraction > 0f -> head
                i < on -> lit
                else -> unlit
            }
            drawCircle(c, d / 2, Offset(i * step + d / 2, d / 2))
        }
    }
}
