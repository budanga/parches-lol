package com.budanga.parcheslol

import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.ui.isDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun temaSegunPreferencia() {
        assertTrue(isDarkTheme("system", systemDark = true))
        assertFalse(isDarkTheme("system", systemDark = false))
        assertTrue(isDarkTheme("dark", systemDark = false))
        assertFalse(isDarkTheme("light", systemDark = true))
    }

    @Test fun preferenciaDeTemaPorDefectoSistema() = runTest {
        val prefs = Prefs(MemoryDataStore())
        assertEquals("system", prefs.theme.first())
        prefs.setTheme("dark")
        assertEquals("dark", prefs.theme.first())
    }
}
