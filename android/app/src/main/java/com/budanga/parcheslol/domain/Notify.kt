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
