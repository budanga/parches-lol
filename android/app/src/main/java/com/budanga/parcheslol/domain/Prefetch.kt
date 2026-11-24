package com.budanga.parcheslol.domain

import com.budanga.parcheslol.data.Catalog
import com.budanga.parcheslol.data.Entity

/**
 * Every portrait the app can show, in the order to load them: favourite champions, the other
 * champions, favourite items, the other items. [favItems] null means the default ADC items.
 */
fun prefetchOrder(catalog: Catalog, favChampions: Set<String>, favItems: Set<String>?): List<Entity> {
    val (myChamps, otherChamps) = catalog.champions.partition { it.id in favChampions }
    val (myItems, otherItems) = catalog.items.partition { favItems?.contains(it.id) ?: isDefaultAdcItem(it.tags) }
    return (myChamps + otherChamps).map { Entity("champion", it.id, it.name) } +
        (myItems + otherItems).map { Entity("item", it.id, it.name) }
}
