package com.budanga.parcheslol.anim

import kotlin.math.floor
import kotlin.random.Random

const val GLYPHS = "░▒▓#%&*+=:<>/\\"
private const val KEEP = " /.,%"

/** One frame of the ASCII "decipher" effect: revealed prefix, separators kept, the rest random glyphs. */
fun decipherFrame(final: String, frame: Int, totalFrames: Int, random: Random): String {
    val shown = floor(frame.toDouble() / totalFrames * final.length).toInt()
    return buildString {
        final.forEachIndexed { i, c ->
            append(if (i < shown || c in KEEP) c else GLYPHS[random.nextInt(GLYPHS.length)])
        }
    }
}

val BAYER4: Array<IntArray> = arrayOf(
    intArrayOf(0, 8, 2, 10),
    intArrayOf(12, 4, 14, 6),
    intArrayOf(3, 11, 1, 9),
    intArrayOf(15, 7, 13, 5),
)

/** Ordered (Bayer 4×4) dithering: whether cell (x, y) is "on" at [progress] in 0..1. */
fun ditherOn(x: Int, y: Int, progress: Float): Boolean = progress > (BAYER4[y % 4][x % 4] + 0.5f) / 16f
