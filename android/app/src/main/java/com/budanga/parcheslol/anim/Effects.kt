package com.budanga.parcheslol.anim

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budanga.parcheslol.data.Change
import com.budanga.parcheslol.domain.Segment
import com.budanga.parcheslol.domain.highlightAfter
import com.budanga.parcheslol.ui.ChangeLine
import com.budanga.parcheslol.ui.Ink
import com.budanga.parcheslol.ui.Type
import com.budanga.parcheslol.ui.afterLine
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** One frame at 12 fps, the brand's pixel cadence. */
const val FRAME_MS = 83L

/** Android's "Remove animations" (animator scale 0) means: no motion, show content directly. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Text that resolves from random glyphs to its real characters. */
@Composable
fun DecipherText(text: String, style: TextStyle, play: Boolean, frames: Int = 10, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    var shown by remember(text) { mutableStateOf(if (play && !reduce) decipherFrame(text, 0, frames, Random) else text) }
    LaunchedEffect(text, play) {
        if (!play || reduce) { shown = text; return@LaunchedEffect }
        for (f in 0..frames) { shown = decipherFrame(text, f, frames, Random); delay(FRAME_MS) }
        shown = text
    }
    Text(shown, style = style, modifier = modifier)
}

/** A change line whose changed numbers decipher the first time the patch is opened. */
@Composable
fun DecipherChangeLine(change: Change, frames: Int = 10) {
    val reduce = rememberReduceMotion()
    var frame by remember(change.id) { mutableIntStateOf(if (reduce) frames else 0) }
    LaunchedEffect(change.id) {
        while (frame < frames) { delay(FRAME_MS); frame++ }
    }
    ChangeLine(change, afterContent = { final, inline ->
        if (frame >= frames) Text(final, style = Type.Data, inlineContent = inline)
        else {
            val segments = highlightAfter(change.before.orEmpty(), change.after.orEmpty()).map {
                if (it.changed) Segment(decipherFrame(it.text, frame, frames, Random), true) else it
            }
            Text(afterLine(segments, change.verification), style = Type.Data, inlineContent = inline)
        }
    })
}

/** Draws [color] cells of [cell] size wherever [on] says so. */
@Composable
private fun DitherCanvas(modifier: Modifier, cell: Dp, color: Color, on: (x: Int, y: Int) -> Boolean) {
    val px = with(LocalDensity.current) { cell.toPx() }
    Canvas(modifier) {
        val cols = (size.width / px).toInt() + 1
        val rows = (size.height / px).toInt() + 1
        for (y in 0 until rows) for (x in 0 until cols) {
            if (on(x, y)) drawRect(color, Offset(x * px, y * px), Size(px, px))
        }
    }
}

/** Loading placeholder: a faint dithered glow drifting over the paper at 12 fps. */
@Composable
fun DitherShimmer(rows: Int = 6) {
    val reduce = rememberReduceMotion()
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduce) { if (!reduce) while (true) { delay(FRAME_MS); phase++ } }
    Column(Modifier.fillMaxWidth()) {
        repeat(rows) { r ->
            Spacer(Modifier.height(14.dp))
            DitherCanvas(Modifier.fillMaxWidth().height(if (r == 0) 40.dp else 18.dp), 4.dp, Ink.Input) { x, y ->
                ditherOn(x, y, 0.45f + 0.35f * sin((x - phase) / 6f))
            }
        }
    }
}

/** Screen transition: terracotta cells cover the screen as [progress] goes 0 → 1. */
@Composable
fun DitherCover(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    DitherCanvas(modifier, 8.dp, Ink.Accent) { x, y -> ditherOn(x, y, progress) }
}

/** First appearance of a not-yet-seen row: paper cells withdraw in ~500 ms. */
@Composable
fun DitherReveal(play: Boolean, content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    var progress by remember { mutableFloatStateOf(if (play && !reduce) 0f else 1f) }
    LaunchedEffect(play) {
        if (play && !reduce) for (f in 1..6) { delay(FRAME_MS); progress = f / 6f }
        progress = 1f
    }
    Box {
        content()
        if (progress < 1f) DitherCanvas(Modifier.matchParentSize(), 4.dp, Ink.Paper) { x, y -> !ditherOn(x, y, progress) }
    }
}
