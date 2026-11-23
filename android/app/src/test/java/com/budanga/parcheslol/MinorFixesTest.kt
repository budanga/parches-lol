package com.budanga.parcheslol

import com.budanga.parcheslol.data.AppJson
import com.budanga.parcheslol.data.Entity
import com.budanga.parcheslol.data.HistoryEntry
import com.budanga.parcheslol.data.PatchFile
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.adcGroups
import com.budanga.parcheslol.domain.allGroups
import com.budanga.parcheslol.domain.shortDate
import com.budanga.parcheslol.domain.splitModes
import com.budanga.parcheslol.domain.verificationAltText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinorFixesTest {
    private val patch = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))

    @Test fun textoAccesibleSegunVerificacion() {
        assertEquals("✓", verificationAltText("verified"))
        assertEquals("⚠", verificationAltText("mismatch"))
    }

    @Test fun seccionDesconocidaNoSePierde() {
        val extra = change(section = "nueva", kind = "text", entity = Entity("none", null, ""))
        val p = patch.copy(changes = patch.changes + extra)
        assertTrue(allGroups(p).any { g -> extra in g.changes && g.collapsed })
        val h = splitModes(HistoryEntry("26.23", "2026-11-17T19:00:00Z", listOf(extra)))
        assertTrue(h.rift.isEmpty())
        assertEquals(listOf(extra), h.modes)
    }

    @Test fun clavesUnicasConSeccion() {
        val dup = change(section = "systems", entity = Entity("champion", "Azir", "Azir"))
        val keys = allGroups(patch.copy(changes = patch.changes + dup)).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun fechaMalFormadaNoRompe() {
        assertEquals("", shortDate("no es una fecha"))
    }

    @Test fun itemPorNombreAunqueCambieElId() {
        val storm = change(section = "items", entity = Entity("item", "3097", "Filo de la Tormenta"))
        val p = patch.copy(changes = listOf(storm))
        val groups = adcGroups(p, DEFAULT_ADCS, myItems = setOf("3095"), myItemNames = setOf("Filo de la Tormenta"))
        assertTrue(groups.any { it.entity?.id == "3097" })
    }
}
