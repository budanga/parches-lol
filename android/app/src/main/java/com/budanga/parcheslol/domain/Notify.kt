package com.budanga.parcheslol.domain

import com.budanga.parcheslol.data.IndexFile
import com.budanga.parcheslol.data.NewsItem

data class PatchAlert(val patch: String, val adcChanges: Int)

/** Patches not announced yet, oldest first. The first run only records, it never announces. */
fun patchesToNotify(index: IndexFile, notified: Set<String>, firstRun: Boolean): List<String> =
    if (firstRun) emptyList() else index.patches.map { it.patch }.filter { it !in notified }.reversed()

fun alertText(alert: PatchAlert): Pair<String, String?> {
    val body = when (alert.adcChanges) {
        0 -> null
        1 -> "1 cambio a tus ADC"
        else -> "${alert.adcChanges} cambios a tus ADC"
    }
    return "Salió el parche ${alert.patch}" to body
}

/** The newest item, if it differs from the last one seen. With nothing seen yet it only records. */
fun newsToNotify(news: List<NewsItem>, lastUrl: String?): NewsItem? {
    val newest = news.maxByOrNull { it.publishedAt } ?: return null
    return if (lastUrl != null && newest.url != lastUrl) newest else null
}

/** What to record as announced: everything on the first run, otherwise only what was actually delivered. */
fun toMarkNotified(index: IndexFile, firstRun: Boolean, delivered: Collection<String>): List<String> =
    if (firstRun) index.patches.map { it.patch } else delivered.toList()

private val PATCH_ID = Regex("""^\d{2}\.(?:S\d+\.)?\d+$""")

/** Where tapping a patch notification leads; anything that isn't a patch number opens the home screen. */
fun routeForNotification(patch: String?): String? = patch?.takeIf { PATCH_ID.matches(it) }?.let { "patch/$it" }
