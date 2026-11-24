package com.budanga.parcheslol

import com.budanga.parcheslol.data.Catalog
import com.budanga.parcheslol.data.CatalogEntry
import com.budanga.parcheslol.domain.prefetchOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefetchOrderTest {
    private val catalog = Catalog(
        schemaVersion = 1, ddragonVersion = "16.23.1",
        champions = listOf(CatalogEntry("Aatrox", "Aatrox"), CatalogEntry("Jinx", "Jinx"), CatalogEntry("Zed", "Zed")),
        items = listOf(CatalogEntry("1001", "Botas"), CatalogEntry("3031", "Filo Infinito")),
    )

    @Test fun favoritosPrimeroLuegoCampeonesLuegoObjetos() {
        val order = prefetchOrder(catalog, setOf("Zed"), setOf("3031"))
        assertEquals(
            listOf("champion:Zed", "champion:Aatrox", "champion:Jinx", "item:3031", "item:1001"),
            order.map { "${it.kind}:${it.id}" },
        )
    }
}
