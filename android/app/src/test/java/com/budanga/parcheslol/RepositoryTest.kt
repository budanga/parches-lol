package com.budanga.parcheslol

import com.budanga.parcheslol.data.Loaded
import com.budanga.parcheslol.data.Repository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private val server = MockWebServer()
    private lateinit var cache: File
    private val indexJson = resource("index.json")

    @Before fun setUp() { server.start(); cache = Files.createTempDirectory("cache").toFile() }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun repo(base: String = server.url("/data/").toString()) = Repository(base, cache)

    @Test fun frescoYGuardaCache() = runTest {
        server.enqueue(MockResponse().setBody(indexJson))
        val r = repo().index()
        assertTrue(r is Loaded.Fresh)
        assertEquals("26.23", (r as Loaded.Fresh).value.patches.first().patch)
        assertEquals("/data/index.json", server.takeRequest().path)
        assertTrue(File(cache, "index.json").exists())
    }

    @Test fun sinRedConCache() = runTest {
        File(cache, "index.json").writeText(indexJson)
        val base = server.url("/data/").toString()
        server.shutdown()
        val r = repo(base).index()
        assertTrue(r is Loaded.Cached)
        assertEquals(47, (r as Loaded.Cached).value.patches.size)
    }

    @Test fun sinRedSinCache() = runTest {
        val base = server.url("/data/").toString()
        server.shutdown()
        assertEquals(Loaded.Offline, repo(base).index())
    }

    @Test fun error500UsaCache() = runTest {
        File(cache, "index.json").writeText(indexJson)
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repo().index() is Loaded.Cached)
    }

    @Test fun jsonRotoNoPisaCache() = runTest {
        File(cache, "index.json").writeText(indexJson)
        server.enqueue(MockResponse().setBody("{roto"))
        assertTrue(repo().index() is Loaded.Cached)
        assertEquals(indexJson, File(cache, "index.json").readText())
        server.enqueue(MockResponse().setBody("{roto"))
        File(cache, "index.json").delete()
        assertEquals(Loaded.Offline, repo().index())
    }

    @Test fun parcheEnSuRuta() = runTest {
        server.enqueue(MockResponse().setBody(resource("patches/26.23.json")))
        assertTrue(repo().patch("26.23") is Loaded.Fresh)
        assertEquals("/data/patches/26.23.json", server.takeRequest().path)
        assertTrue(File(cache, "patches/26.23.json").exists())
    }

    @Test fun noExisteEsMissing() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(Loaded.Missing, repo().champion("Teemo"))
    }
}
