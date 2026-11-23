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
        val key = if (c.entity.id != null) "${c.entity.kind}:${c.entity.id}" else "${c.section}:${c.sectionTitle}"
        groups.getOrPut(key) { mutableListOf() } += c
    }
    return groups.map { (key, list) ->
        val first = list.first()
        val entity = first.entity.takeIf { it.id != null }
        Group(key, entity?.name ?: first.sectionTitle, entity, list, collapsed = false)
    }
}

fun adcGroups(patch: PatchFile, myAdcs: Set<String>, myItems: Set<String>?): List<Group> {
    val champions = patch.changes.filter {
        it.section == "champions" && it.entity.kind == "champion" && it.entity.id in myAdcs
    }
    val items = patch.changes.filter {
        it.section == "items" && it.entity.kind == "item" && it.entity.id != null &&
            (myItems?.contains(it.entity.id) ?: isDefaultAdcItem(it.tags))
    }
    val global = patch.changes.filter { it.section == "systems" }
    return groupByEntity(champions) + groupByEntity(items) +
        listOfNotNull(global.takeIf { it.isNotEmpty() }?.let { Group("global", "Global", null, it, false) })
}

fun allGroups(patch: PatchFile): List<Group> {
    val main = listOf("champions", "items", "runes", "systems")
        .flatMap { section -> groupByEntity(patch.changes.filter { it.section == section }) }
    val folded = listOf("modes" to "Otros modos", "cosmetics" to "Cosméticos").mapNotNull { (section, title) ->
        patch.changes.filter { it.section == section }.takeIf { it.isNotEmpty() }
            ?.let { Group(section, title, null, it, collapsed = true) }
    }
    return main + folded
}
