package com.budanga.parcheslol.domain

/** Colour family of an official tag: green new, red removed, yellow anything else (reworked, updated…). */
enum class TagRole { NEW, REMOVED, CHANGED }

fun tagRole(tag: String): TagRole {
    val t = tag.uppercase()
    return when {
        t.startsWith("NUEV") -> TagRole.NEW
        t.startsWith("ELIMINAD") || t.startsWith("RETIRAD") -> TagRole.REMOVED
        else -> TagRole.CHANGED
    }
}

private val MARKER = Regex("""^(NUEVO|NUEVA|ELIMINADO|ELIMINADA)\b""", RegexOption.IGNORE_CASE)

/** "NUEVO: Ahora otorga…" → "NUEVO": the official word at the start of a line, if any. */
fun leadingMarker(text: String): String? = MARKER.find(text.trimStart())?.value
