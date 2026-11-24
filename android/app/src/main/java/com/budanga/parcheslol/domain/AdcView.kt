package com.budanga.parcheslol.domain

import com.budanga.parcheslol.data.Change
import com.budanga.parcheslol.data.Entity
import com.budanga.parcheslol.data.PatchFile

/** Bot-lane marksmen the app starts with; editable in Settings. */
val DEFAULT_ADCS: Set<String> = setOf(
    "Aphelios", "Ashe", "Caitlyn", "Corki", "Draven", "Ezreal", "Jhin", "Jinx", "Kaisa", "Kalista",
    "KogMaw", "Lucian", "MissFortune", "Nilah", "Samira", "Senna", "Sivir", "Smolder", "Tristana",
    "Twitch", "Varus", "Vayne", "Xayah", "Yunara", "Zeri",
)

fun isDefaultAdcItem(tags: List<String>): Boolean =
    "CriticalStrike" in tags || "AttackSpeed" in tags || ("ArmorPenetration" in tags && "Damage" in tags)

private val RIFT_SECTIONS = listOf("champions", "items", "runes", "systems")

data class Group(
    val key: String,
    val title: String,
    val entity: Entity?,
    val changes: List<Change>,
    val collapsed: Boolean,
)

/** One group per entity (or per section title when there is none), in page order. */
private fun groupByEntity(changes: List<Change>): List<Group> {
    val groups = LinkedHashMap<String, MutableList<Change>>()
    for (c in changes) {
        val key = if (c.entity.id != null) "${c.section}:${c.entity.kind}:${c.entity.id}" else "${c.section}:${c.sectionTitle}"
        groups.getOrPut(key) { mutableListOf() } += c
    }
    return groups.map { (key, list) ->
        val first = list.first()
        val entity = first.entity.takeIf { it.id != null }
        Group(key, entity?.name ?: first.sectionTitle, entity, list, collapsed = false)
    }
}

/** [myItemNames] also matches items whose id changed between game versions (same name, new id). */
fun adcGroups(
    patch: PatchFile,
    myAdcs: Set<String>,
    myItems: Set<String>?,
    myItemNames: Set<String> = emptySet(),
): List<Group> {
    val champions = patch.changes.filter {
        it.section == "champions" && it.entity.kind == "champion" && it.entity.id in myAdcs
    }
    val items = patch.changes.filter {
        it.section == "items" && it.entity.kind == "item" && it.entity.id != null &&
            (myItems?.let { ids -> it.entity.id in ids || it.entity.name in myItemNames } ?: isDefaultAdcItem(it.tags))
    }
    val global = patch.changes.filter { it.section == "systems" }
    return groupByEntity(champions) + groupByEntity(items) +
        listOfNotNull(global.takeIf { it.isNotEmpty() }?.let { Group("global", "Global", null, it, false) })
}

fun allGroups(patch: PatchFile): List<Group> {
    val main = RIFT_SECTIONS.flatMap { section -> groupByEntity(patch.changes.filter { it.section == section }) }
    val folded = listOf("modes" to "Otros modos", "cosmetics" to "Cosméticos").mapNotNull { (section, title) ->
        patch.changes.filter { it.section == section }.takeIf { it.isNotEmpty() }
            ?.let { Group(section, title, null, it, collapsed = true) }
    }
    // A section this app doesn't know yet is still shown, never dropped.
    val unknown = patch.changes.filter { it.section !in RIFT_SECTIONS && it.section != "modes" && it.section != "cosmetics" }
        .takeIf { it.isNotEmpty() }?.let { Group("other", "Otros", null, it, collapsed = true) }
    return main + folded + listOfNotNull(unknown)
}

data class HistoryView(val rift: List<Change>, val modes: List<Change>)

/** History entries keep game-mode changes (ARAM, Arena, Classic…) apart so they never read as Rift changes. */
fun splitModes(entry: com.budanga.parcheslol.data.HistoryEntry): HistoryView =
    entry.changes.partition { it.section in RIFT_SECTIONS }.let { (rift, modes) -> HistoryView(rift, modes) }

/** A row of a group: a sub-header (1 = section, 2 = champion/item/named block, 3 = sub-part) or a change. */
sealed interface RowItem {
    data class Header(val text: String, val level: Int, val entity: Entity? = null, val tag: String? = null) : RowItem
    data class Line(val change: Change) : RowItem
}

private val ABILITY_PARTS = setOf("base", "P", "Q", "W", "E", "R")

/**
 * Lays out a group with the context each line needs: groups that mix sections (Global, Otros modos,
 * Cosméticos) get section and entity sub-headers; generic sub-parts ("Firmamento único") get a label.
 */
fun rowsFor(group: Group): List<RowItem> {
    val mixed = group.entity == null
    val out = mutableListOf<RowItem>()
    var section: String? = null
    var entityKey: String? = null
    var part: String? = null
    for (c in group.changes) {
        if (mixed && c.sectionTitle != section) {
            if (c.sectionTitle != group.title) out += RowItem.Header(c.sectionTitle, 1)
            section = c.sectionTitle; entityKey = null; part = null
        }
        val key = c.entity.id ?: c.entity.name
        val p = c.part
        val genericPart = p != null && p !in ABILITY_PARTS && p != c.entity.name
        val entityChanged = mixed && key != entityKey
        // A sub-part heading that changes together with the entity came first on the page ("Campeones" → Talon).
        if (entityChanged && genericPart && p != part) { out += RowItem.Header(p!!, 3); part = p }
        if (entityChanged) {
            if (c.entity.name.isNotBlank() && c.entity.name != c.sectionTitle)
                out += RowItem.Header(c.entity.name, 2, c.entity.takeIf { it.id != null }, c.tag)
            entityKey = key
        }
        if (genericPart && p != part) out += RowItem.Header(p!!, 3)
        part = p
        // The heading line itself ("Trituradoras de Malla") is already shown as the header.
        if (c.kind == "text" && c.text == c.entity.name) continue
        out += RowItem.Line(c)
    }
    return out
}

/**
 * Search inside a patch: a group whose name matches is kept whole; otherwise only its matching lines.
 * Folded groups with matches open up. Accents, case and apostrophes are ignored.
 */
fun filterGroups(groups: List<Group>, query: String): List<Group> {
    val q = normalizeForSearch(query.trim())
    if (q.isEmpty()) return groups
    return groups.mapNotNull { g ->
        if (normalizeForSearch(g.title).contains(q)) return@mapNotNull g.copy(collapsed = false)
        val lines = g.changes.filter { c ->
            listOfNotNull(c.text, c.attribute, c.sectionTitle, c.entity.name, c.partTitle).any { normalizeForSearch(it).contains(q) }
        }
        if (lines.isEmpty()) null else g.copy(changes = lines, collapsed = false)
    }
}

/** Portraits for the home-screen widget: your ADCs and ADC items changed in this patch, in page order. */
fun widgetEntities(patch: PatchFile, myAdcs: Set<String>, myItems: Set<String>?, myItemNames: Set<String> = emptySet()): List<Entity> =
    adcGroups(patch, myAdcs, myItems, myItemNames).mapNotNull { it.entity }

/** Favourite champions and items changed in a patch, from the index (no need to download the patch). */
fun favoritesIn(
    summary: com.budanga.parcheslol.data.PatchSummary,
    favChampions: Set<String>,
    favItems: Set<String>?,
    favItemNames: Set<String> = emptySet(),
): List<Entity> = summary.changed.filter { e ->
    when (e.kind) {
        "champion" -> e.id in favChampions
        "item" -> favItems?.let { e.id in it || e.name in favItemNames } ?: isDefaultAdcItem(e.tags)
        else -> false
    }
}.map { Entity(it.kind, it.id, it.name) }
