package com.budanga.parcheslol

import com.budanga.parcheslol.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationPrefsTest {
    @Test fun todasActivasPorDefectoYSeApaganPorSeparado() = runTest {
        val prefs = Prefs(MemoryDataStore())
        assertEquals(listOf(true, true, true), listOf(prefs.animTitle.first(), prefs.animTransition.first(), prefs.animNumbers.first()))
        prefs.setAnimTransition(false)
        assertEquals(listOf(true, false, true), listOf(prefs.animTitle.first(), prefs.animTransition.first(), prefs.animNumbers.first()))
    }
}
