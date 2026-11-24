package com.budanga.parcheslol

import com.budanga.parcheslol.data.CatalogEntry
import com.budanga.parcheslol.domain.favoritePickerRows
import com.budanga.parcheslol.domain.showsVerificationIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesPickerTest {
    private val all = listOf(
        CatalogEntry("Ashe", "Ashe"),
        CatalogEntry("Kaisa", "Kai'Sa"),
        CatalogEntry("Jinx", "Jinx"),
        CatalogEntry("Caitlyn", "Caitlyn"),
    )

    @Test fun sinBusquedaSoloMuestraLosFavoritosPorNombre() {
        val rows = favoritePickerRows(all, setOf("Jinx", "Ashe"), "")
        assertEquals(listOf("Ashe", "Jinx"), rows.map { it.id })
    }

    @Test fun conBusquedaMuestraTodosLosQueCoinciden() {
        val rows = favoritePickerRows(all, setOf("Jinx"), "kais")
        assertEquals(listOf("Kaisa"), rows.map { it.id })
        // Spaces around the query don't hide results.
        assertEquals(listOf("Caitlyn"), favoritePickerRows(all, emptySet(), "  cait ").map { it.id })
    }

    @Test fun soloLasDiferenciasLlevanIcono() {
        assertTrue(showsVerificationIcon("mismatch"))
        assertFalse(showsVerificationIcon("verified"))
        assertFalse(showsVerificationIcon("unchecked"))
    }
}
