package com.budanga.parcheslol

import com.budanga.parcheslol.data.AppJson
import com.budanga.parcheslol.data.PatchFile
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.adcGroups
import com.budanga.parcheslol.domain.allGroups
import com.budanga.parcheslol.domain.isDefaultAdcItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdcViewTest {
    private val patch = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))

    @Test fun ezrealPrimero() {
        val first = adcGroups(patch, DEFAULT_ADCS, null).first()
        assertEquals("Ezreal", first.entity?.id)
        assertEquals(2, first.changes.size)
    }

    @Test fun sinAzirNiModos() {
        val groups = adcGroups(patch, DEFAULT_ADCS, null)
        assertTrue(groups.none { it.entity?.id == "Azir" })
        assertTrue(groups.flatMap { it.changes }.none { it.section == "modes" })
    }

    @Test fun grupoGlobal() {
        val global = adcGroups(patch, DEFAULT_ADCS, null).single { it.key == "global" }
        assertEquals("Global", global.title)
        assertTrue(global.changes.any { it.text.startsWith("El chat de voz de equipo está habilitado") })
    }

    @Test fun sinCambiosParaTusAdc() {
        val groups = adcGroups(patch, setOf("Teemo"), emptySet())
        assertTrue(groups.none { it.entity != null })
    }

    @Test fun todoConPlegados() {
        val groups = allGroups(patch)
        assertTrue(groups.last().collapsed)
        assertEquals(listOf("Otros modos", "Cosméticos"), groups.takeLast(2).map { it.title })
        assertTrue(groups.any { it.entity?.id == "Azir" })
    }

    @Test fun itemsAdcPorDefecto() {
        assertTrue(isDefaultAdcItem(listOf("CriticalStrike", "Damage")))
        assertTrue(isDefaultAdcItem(listOf("ArmorPenetration", "Damage")))
        assertFalse(isDefaultAdcItem(listOf("SpellDamage")))
    }
}
