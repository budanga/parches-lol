package com.budanga.parcheslol.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirrors pipeline/src/types.ts. Unknown fields are ignored so the data can grow. */
val AppJson: Json = Json { ignoreUnknownKeys = true }

@Serializable
data class Entity(val kind: String, val id: String? = null, val name: String)

@Serializable
data class GameData(val before: String, val after: String)

@Serializable
data class Change(
    val id: String,
    val sectionTitle: String,
    val sectionId: String,
    val midPatch: Boolean = false,
    val entity: Entity,
    val part: String? = null,
    val partTitle: String? = null,
    val spellKey: String? = null,
    /** Official heading tag ("NUEVO", "ELIMINADO", "REDISEÑADO"…), if the block had one. */
    val tag: String? = null,
    val kind: String,
    val attribute: String? = null,
    val before: String? = null,
    val after: String? = null,
    val text: String,
    val section: String,
    val tags: List<String> = emptyList(),
    val verification: String,
    val gameData: GameData? = null,
    val addedLater: Boolean = false,
)

@Serializable
data class PatchFile(
    val schemaVersion: Int,
    val patch: String,
    val title: String,
    val url: String,
    val publishedAt: String,
    val ddragonVersion: String,
    val changes: List<Change>,
)

@Serializable
data class Counts(val total: Int, val verified: Int, val unchecked: Int, val mismatch: Int)

@Serializable
data class ChangedEntity(val kind: String, val id: String, val name: String, val tags: List<String> = emptyList())

@Serializable
data class PatchSummary(
    val patch: String,
    val title: String,
    val url: String,
    val publishedAt: String,
    val counts: Counts,
    /** Champions and items changed on the Rift (older indexes don't have it). */
    val changed: List<ChangedEntity> = emptyList(),
)

@Serializable
data class IndexFile(val schemaVersion: Int, val updatedAt: String, val patches: List<PatchSummary>)

@Serializable
data class HistoryEntry(val patch: String, val publishedAt: String, val changes: List<Change>)

@Serializable
data class ChampionHistory(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val tags: List<String>,
    val entries: List<HistoryEntry>,
)

@Serializable
data class NewsItem(
    val title: String,
    val publishedAt: String,
    val url: String,
    val imageUrl: String? = null,
    val description: String? = null,
    val category: String,
)

@Serializable
data class CatalogEntry(val id: String, val name: String, val tags: List<String> = emptyList())

@Serializable
data class Catalog(
    val schemaVersion: Int,
    val ddragonVersion: String,
    val champions: List<CatalogEntry>,
    val items: List<CatalogEntry>,
)
