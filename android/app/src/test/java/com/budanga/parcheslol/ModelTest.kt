package com.budanga.parcheslol

import com.budanga.parcheslol.data.AppJson
import com.budanga.parcheslol.data.Catalog
import com.budanga.parcheslol.data.Change
import com.budanga.parcheslol.data.IndexFile
import com.budanga.parcheslol.data.PatchFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

fun resource(path: String): String =
    requireNotNull(object {}.javaClass.classLoader!!.getResource(path)) { "falta $path" }.readText()

class ModelTest {
    @Test fun indiceReal() {
        val index = AppJson.decodeFromString<IndexFile>(resource("index.json"))
        assertEquals("26.23", index.patches.first().patch)
        assertEquals(47, index.patches.size)
    }

    @Test fun parcheReal() {
        val patch = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))
        val azir = patch.changes.first { it.entity.id == "Azir" && it.part == "base" }
        assertEquals("525", azir.before)
        assertEquals("550", azir.after)
        assertEquals("verified", azir.verification)
    }

    @Test fun camposNuevosSeIgnoran() {
        val patch = AppJson.decodeFromString<PatchFile>(resource("patches/26.23.json"))
        val json = AppJson.encodeToString(Change.serializer(), patch.changes.first())
            .replaceFirst("{", "{\"nuevoCampo\":1,")
        assertEquals(patch.changes.first(), AppJson.decodeFromString<Change>(json))
    }

    @Test fun catalogo() {
        val catalog = AppJson.decodeFromString<Catalog>(resource("catalog.json"))
        assertTrue(catalog.champions.any { it.id == "MonkeyKing" })
    }
}
