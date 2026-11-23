package com.budanga.parcheslol

import com.budanga.parcheslol.anim.PlayOnce
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayOnceTest {
    @Test fun cadaLineaSeAnimaUnaSolaVez() {
        val once = PlayOnce()
        assertTrue(once.claim("a"))
        assertFalse(once.claim("a"))
        assertTrue(once.claim("b"))
        assertFalse(once.claim("a"))
    }
}
