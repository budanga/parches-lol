package com.budanga.parcheslol

import com.budanga.parcheslol.data.AppJson
import com.budanga.parcheslol.data.IndexFile
import com.budanga.parcheslol.data.NewsItem
import com.budanga.parcheslol.domain.PatchAlert
import com.budanga.parcheslol.domain.alertText
import com.budanga.parcheslol.domain.newsToNotify
import com.budanga.parcheslol.domain.patchesToNotify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyTest {
    private val index = AppJson.decodeFromString<IndexFile>(resource("index.json"))
    private val all = index.patches.map { it.patch }.toSet()

    @Test fun primeraInstalacionNoAvisa() = assertTrue(patchesToNotify(index, emptySet(), firstRun = true).isEmpty())

    @Test fun avisaUnaSolaVez() {
        val first = patchesToNotify(index, all - "26.23", firstRun = false)
        assertEquals(listOf("26.23"), first)
        assertTrue(patchesToNotify(index, all, firstRun = false).isEmpty())
    }

    @Test fun variosNuevosDelMasViejoAlMasNuevo() =
        assertEquals(listOf("26.22", "26.23"), patchesToNotify(index, all - "26.23" - "26.22", firstRun = false))

    @Test fun textos() {
        assertEquals("Salió el parche 26.24" to "4 cambios a tus ADC", alertText(PatchAlert("26.24", 4)))
        assertEquals("1 cambio a tus ADC", alertText(PatchAlert("26.24", 1)).second)
        assertNull(alertText(PatchAlert("26.24", 0)).second)
    }

    @Test fun noticias() {
        val news = listOf(
            NewsItem("Nueva", "2026-11-20T00:00:00Z", "u2", category = "dev"),
            NewsItem("Vieja", "2026-11-10T00:00:00Z", "u1", category = "dev"),
        )
        assertNull(newsToNotify(news, null))
        assertEquals("u2", newsToNotify(news, "u1")?.url)
        assertNull(newsToNotify(news, "u2"))
    }
}
