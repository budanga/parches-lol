package com.budanga.parcheslol

import com.budanga.parcheslol.data.AppJson
import com.budanga.parcheslol.data.IndexFile
import com.budanga.parcheslol.data.PatchFile
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.RowItem
import com.budanga.parcheslol.domain.adcGroups
import com.budanga.parcheslol.domain.allGroups
import com.budanga.parcheslol.domain.highlightAfter
import com.budanga.parcheslol.domain.rowsFor
import com.budanga.parcheslol.domain.toMarkNotified
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFixesTest {
    private val index = AppJson.decodeFromString<IndexFile>(resource("index.json"))
    private val p2623 = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))
    private val p25 = AppJson.decodeFromString<PatchFile>(resource("patches/25.S1.1.json"))

    // I1: a patch whose file failed to download must stay unnotified.
    @Test fun soloSeMarcanLosAvisados() {
        assertEquals(index.patches.size, toMarkNotified(index, firstRun = true, delivered = emptyList()).size)
        assertEquals(listOf("26.22"), toMarkNotified(index, firstRun = false, delivered = listOf("26.22")))
    }

    // I2: mode lines are labelled with their mode and their champion.
    @Test fun otrosModosConSubtitulos() {
        val modes = allGroups(p2623).first { it.key == "modes" }
        val rows = rowsFor(modes)
        assertTrue(rows.any { it is RowItem.Header && it.level == 1 && it.text == "Classic" })
        val talon = rows.indexOfFirst { it is RowItem.Header && it.level == 2 && it.entity?.id == "Talon" }
        assertTrue(talon >= 0)
        assertTrue(rows[talon + 1] is RowItem.Line)
    }

    // I3: global lines keep their section title and named-but-unresolved headings.
    @Test fun globalConSecciones() {
        val global = adcGroups(p25, DEFAULT_ADCS, null).first { it.key == "global" }
        val rows = rowsFor(global)
        assertTrue(rows.first() is RowItem.Header && (rows.first() as RowItem.Header).level == 1)
        val malla = rows.indexOfFirst { it is RowItem.Header && it.text == "Trituradoras de Malla" }
        assertTrue(malla >= 0)
        assertTrue((rows.drop(malla + 1).first { it is RowItem.Line } as RowItem.Line).change.attribute == "Monto de escudo")
        assertTrue(rows.any { it is RowItem.Header && it.level == 1 && it.text == "Teleportación" })
    }

    // I4: the cached copy can be read without touching the network.
    @Test fun cacheSinRed() = runTest {
        val dir = Files.createTempDirectory("c").toFile()
        val repo = Repository("http://127.0.0.1:9/", dir)
        assertNull(repo.cachedIndex())
        File(dir, "index.json").writeText(resource("index.json"))
        assertEquals("26.23", repo.cachedIndex()?.patches?.first()?.patch)
    }

    // M6 (re-graded): two quick toggles both land.
    @Test fun dosToquesSeguidos() = runTest {
        val prefs = Prefs(MemoryDataStore())
        listOf(async { prefs.toggleAdc("Teemo", true) }, async { prefs.toggleAdc("Ezreal", false) }).awaitAll()
        val adcs = prefs.myAdcs.first()
        assertTrue("Teemo" in adcs)
        assertTrue("Ezreal" !in adcs)
        assertEquals(DEFAULT_ADCS.size, adcs.size)
    }

    // M10 (re-graded): the hyphen of a range is not part of the number.
    @Test fun guionDeRango() {
        val s = highlightAfter("10-140 (según el nivel)", "10-120 (según el nivel)")
        assertEquals(listOf("120"), s.filter { it.changed }.map { it.text })
    }
}

class NoDuplicateTitleTest {
    @Test fun grupoDeUnaSeccionNoRepiteSuTitulo() {
        val p = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))
        val g = allGroups(p).first { it.title.startsWith("Corrección de errores") }
        assertTrue(rowsFor(g).none { it is RowItem.Header && it.text == g.title })
    }
}
