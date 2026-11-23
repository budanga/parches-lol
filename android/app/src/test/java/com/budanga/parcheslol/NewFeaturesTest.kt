package com.budanga.parcheslol

import com.budanga.parcheslol.data.AppJson
import com.budanga.parcheslol.data.Loaded
import com.budanga.parcheslol.data.PatchFile
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.domain.allGroups
import com.budanga.parcheslol.domain.filterGroups
import com.budanga.parcheslol.domain.routeForNotification
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesTest {
    private val patch = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))

    @Test fun historialDeObjetoEnSuRuta() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody(resource("Ezreal.json")))
        val repo = Repository(server.url("/data/").toString(), Files.createTempDirectory("c").toFile())
        assertTrue(repo.item("3031") is Loaded.Fresh)
        assertEquals("/data/items/3031.json", server.takeRequest().path)
        server.shutdown()
    }

    @Test fun buscarPorCampeon() {
        val found = filterGroups(allGroups(patch), "ezreal")
        assertEquals(listOf("Ezreal"), found.map { it.entity?.name })
        assertEquals(2, found.single().changes.size)
    }

    @Test fun buscarPorNombreDeGrupoMuestraElGrupoEntero() {
        val found = filterGroups(allGroups(patch), "chat de voz")
        val group = found.single { it.title == "Chat de voz de equipo" }
        assertEquals(allGroups(patch).single { it.title == "Chat de voz de equipo" }.changes, group.changes)
    }

    @Test fun buscarPorTextoDeLaLinea() {
        val found = filterGroups(allGroups(patch), "illaoi")
        assertEquals(1, found.flatMap { it.changes }.size)
        assertTrue(found.single().changes.single().text.contains("Illaoi"))
    }

    @Test fun busquedaVaciaDevuelveTodo() {
        val groups = allGroups(patch)
        assertEquals(groups, filterGroups(groups, "  "))
    }

    @Test fun avisoAbreElParche() {
        assertEquals("patch/26.24", routeForNotification("26.24"))
        assertNull(routeForNotification(null))
        assertNull(routeForNotification("../x"))
    }
}

class WidgetDataTest {
    @Test fun retratosDeAdcYObjetosConCambios() {
        val p = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))
        val e = com.budanga.parcheslol.domain.widgetEntities(p, com.budanga.parcheslol.domain.DEFAULT_ADCS, null)
        assertEquals(listOf("Ezreal", "6699"), e.map { it.id })
    }
}

class FavoritesRowTest {
    private val summary = AppJson.decodeFromString<com.budanga.parcheslol.data.PatchSummary>("""
        {"patch":"26.23","title":"t","url":"u","publishedAt":"2026-11-17T19:00:00Z",
         "counts":{"total":1,"verified":0,"unchecked":1,"mismatch":0},
         "changed":[{"kind":"champion","id":"Azir","name":"Azir","tags":["Mage"]},
                    {"kind":"champion","id":"Ezreal","name":"Ezreal","tags":["Marksman"]},
                    {"kind":"item","id":"6699","name":"Cicloespada Voltaica","tags":["Damage","ArmorPenetration"]},
                    {"kind":"item","id":"3089","name":"Sombrero","tags":["SpellDamage"]}]}""")

    @Test fun soloLosFavoritosCambiados() {
        val e = com.budanga.parcheslol.domain.favoritesIn(summary, setOf("Ezreal"), null)
        assertEquals(listOf("Ezreal", "6699"), e.map { it.id })
    }

    @Test fun objetosFavoritosExplicitosYPorNombre() {
        val e = com.budanga.parcheslol.domain.favoritesIn(summary, emptySet(), setOf("9999"), setOf("Sombrero"))
        assertEquals(listOf("3089"), e.map { it.id })
    }

    @Test fun indiceViejoSinListaNoRompe() {
        val old = AppJson.decodeFromString<com.budanga.parcheslol.data.PatchSummary>(
            """{"patch":"26.1","title":"t","url":"u","publishedAt":"2026-01-08T00:00:00Z","counts":{"total":0,"verified":0,"unchecked":0,"mismatch":0}}""")
        assertTrue(com.budanga.parcheslol.domain.favoritesIn(old, setOf("Ezreal"), null).isEmpty())
    }
}
