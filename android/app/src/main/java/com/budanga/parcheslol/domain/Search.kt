package com.budanga.parcheslol.domain

import java.text.Normalizer

private val MARKS = Regex("""\p{M}+""")
private val PUNCT = Regex("""['’.]""")

/** Lowercase, no accents, no apostrophes or dots: "Kai'Sa" → "kaisa". */
fun normalizeForSearch(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(PUNCT, "")

/** Favorites picker: with no query only your favorites (by name); with a query every match, so you can add more. */
fun favoritePickerRows(
    entries: List<com.budanga.parcheslol.data.CatalogEntry>,
    selected: Set<String>,
    query: String,
): List<com.budanga.parcheslol.data.CatalogEntry> {
    val q = normalizeForSearch(query.trim())
    return if (q.isEmpty()) entries.filter { it.id in selected }.sortedBy { normalizeForSearch(it.name) }
    else entries.filter { normalizeForSearch(it.name).contains(q) }
}
