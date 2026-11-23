package com.budanga.parcheslol.data

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface Loaded<out T> {
    data class Fresh<T>(val value: T) : Loaded<T>
    data class Cached<T>(val value: T) : Loaded<T>
    data object Offline : Loaded<Nothing>
    /** The server answered 404 and nothing is saved: the file does not exist (e.g. a champion with no history). */
    data object Missing : Loaded<Nothing>
}

/** The value inside a Fresh or Cached result, or null when offline with nothing saved. */
val <T> Loaded<T>.valueOrNull: T?
    get() = when (this) {
        is Loaded.Fresh -> value
        is Loaded.Cached -> value
        Loaded.Offline, Loaded.Missing -> null
    }

const val DATA_BASE_URL = "https://raw.githubusercontent.com/budanga/parches-lol/main/data/"

/** Network first; a good response is saved to disk, anything else falls back to the saved copy. */
class Repository(
    private val baseUrl: String,
    private val cacheDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun index(): Loaded<IndexFile> = load("index.json", IndexFile.serializer())
    suspend fun patch(id: String): Loaded<PatchFile> = load("patches/$id.json", PatchFile.serializer())
    suspend fun champion(id: String): Loaded<ChampionHistory> = load("champions/$id.json", ChampionHistory.serializer())
    suspend fun news(): Loaded<List<NewsItem>> = load("news.json", ListSerializer(NewsItem.serializer()))
    suspend fun catalog(): Loaded<Catalog> = load("catalog.json", Catalog.serializer())

    /** The saved copies, without touching the network (shown first, then refreshed). */
    suspend fun cachedIndex(): IndexFile? = cached("index.json", IndexFile.serializer())
    suspend fun cachedPatch(id: String): PatchFile? = cached("patches/$id.json", PatchFile.serializer())
    suspend fun cachedNews(): List<NewsItem>? = cached("news.json", ListSerializer(NewsItem.serializer()))

    private suspend fun <T> cached(path: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        runCatching { AppJson.decodeFromString(serializer, File(cacheDir, path).readText()) }.getOrNull()
    }

    private suspend fun <T> load(path: String, serializer: KSerializer<T>): Loaded<T> = withContext(Dispatchers.IO) {
        val file = File(cacheDir, path)
        var notFound = false
        val fresh = runCatching {
            client.newCall(Request.Builder().url(baseUrl + path).build()).execute().use { res ->
                if (res.code == 404) notFound = true
                check(res.code == 200) { "HTTP ${res.code}" }
                val body = res.body!!.string()
                val value = AppJson.decodeFromString(serializer, body)
                file.parentFile?.mkdirs()
                // Unique temp name: the worker and the app may save the same file at once.
                val tmp = File.createTempFile(file.name, ".tmp", file.parentFile)
                tmp.writeText(body)
                if (!tmp.renameTo(file)) {
                    // Windows-style rename over an existing file: copy, keeping the old cache if that fails too.
                    runCatching { tmp.copyTo(file, overwrite = true) }
                    tmp.delete()
                }
                value
            }
        }.getOrNull()
        if (fresh != null) return@withContext Loaded.Fresh(fresh)
        val cached = runCatching { AppJson.decodeFromString(serializer, file.readText()) }.getOrNull()
        when {
            cached != null -> Loaded.Cached(cached)
            notFound -> Loaded.Missing
            else -> Loaded.Offline
        }
    }
}
