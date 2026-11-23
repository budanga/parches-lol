package com.budanga.parcheslol.domain

import java.time.Duration
import java.time.Instant

fun updatedText(updatedAt: Instant, now: Instant): String {
    val minutes = Duration.between(updatedAt, now).toMinutes().coerceAtLeast(1)
    return when {
        minutes < 60 -> "Actualizado hace $minutes min"
        minutes < 24 * 60 -> "Actualizado hace ${minutes / 60} h"
        else -> {
            val days = minutes / (24 * 60)
            if (days == 1L) "Actualizado hace 1 día" else "Actualizado hace $days días"
        }
    }
}

private val SHORT_DATE = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("es"))

/** "2026-11-17T19:00:00.000Z" → "17 nov" in the phone's time zone. */
fun shortDate(iso: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    Instant.parse(iso).atZone(zone).format(SHORT_DATE).replace(".", "")
