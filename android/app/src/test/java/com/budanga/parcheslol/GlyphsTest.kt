package com.budanga.parcheslol

import com.budanga.parcheslol.anim.GLYPHS
import com.budanga.parcheslol.anim.decipherFrame
import com.budanga.parcheslol.anim.ditherOn
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphsTest {
    @Test fun descifrado() {
        val r = Random(1)
        assertEquals("115%", decipherFrame("115%", 10, 10, r))
        val f0 = decipherFrame("26.23", 0, 10, r)
        assertEquals(5, f0.length)
        assertEquals('.', f0[2])
        assertTrue(f0.filterIndexed { i, _ -> i != 2 }.all { it in GLYPHS })
    }

    @Test fun bayer() {
        assertTrue((0 until 4).all { y -> (0 until 4).none { x -> ditherOn(x, y, 0f) } })
        assertTrue((0 until 4).all { y -> (0 until 4).all { x -> ditherOn(x, y, 1f) } })
    }
}
