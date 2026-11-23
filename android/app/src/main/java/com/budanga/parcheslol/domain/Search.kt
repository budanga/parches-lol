package com.budanga.parcheslol.domain

import java.text.Normalizer

private val MARKS = Regex("""\p{M}+""")
private val PUNCT = Regex("""['’.]""")

/** Lowercase, no accents, no apostrophes or dots: "Kai'Sa" → "kaisa". */
fun normalizeForSearch(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(PUNCT, "")
