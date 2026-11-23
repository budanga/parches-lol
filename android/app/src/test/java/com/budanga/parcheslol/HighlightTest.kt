package com.budanga.parcheslol

import com.budanga.parcheslol.data.GameData
import com.budanga.parcheslol.domain.highlightAfter
import com.budanga.parcheslol.domain.mismatchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {
    @Test fun ezrealW() {
        val s = highlightAfter(
            "80 / 135 / 190 / 245 / 300 (+100% de Daño de Ataque adicional) (+90% de Poder de Habilidad)",
            "80 / 135 / 190 / 245 / 300 (+115% de Daño de Ataque adicional) (+100% de Poder de Habilidad)")
        assertEquals(listOf("115%", "100%"), s.filter { it.changed }.map { it.text })
        assertEquals("80 / 135 / 190 / 245 / 300 (+115% de Daño de Ataque adicional) (+100% de Poder de Habilidad)", s.joinToString("") { it.text })
    }

    @Test fun ezrealR() = assertEquals(listOf("600", "850", "110%", "120%"),
        highlightAfter("350 / 550 / 750 (+100% de Daño de Ataque adicional) (+110% de Poder de Habilidad)",
            "350 / 600 / 850 (+110% de Daño de Ataque adicional) (+120% de Poder de Habilidad)").filter { it.changed }.map { it.text })

    @Test fun sinCambios() = assertTrue(highlightAfter("525", "525").none { it.changed })

    @Test fun distintaCantidad() = assertEquals(listOf("3", "4"), highlightAfter("1", "3 / 4").filter { it.changed }.map { it.text })

    @Test fun textoMismatch() {
        val base = change(before = "3.25", after = "3.5", verification = "mismatch", gameData = GameData("3.25", "3.75"))
        assertEquals("En el juego figura 3.75", mismatchText(base))
        assertEquals("En el juego figura 110 ⇒ 115", mismatchText(base.copy(before = "100", after = "125", gameData = GameData("110", "115"))))
        assertNull(mismatchText(base.copy(verification = "verified", gameData = null)))
    }
}
