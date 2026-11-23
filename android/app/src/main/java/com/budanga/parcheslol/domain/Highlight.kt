package com.budanga.parcheslol.domain

import com.budanga.parcheslol.data.Change

data class Segment(val text: String, val changed: Boolean)

private val NUMBER = Regex("""(?<!\d)-?\d+(?:[.,]\d+)?%?""")

/**
 * Splits the official "after" line into segments, marking the numbers that differ from "before".
 * The text is never rewritten: the segments concatenate back to [after] exactly.
 */
fun highlightAfter(before: String, after: String): List<Segment> {
    val beforeNums = NUMBER.findAll(before).map { it.value }.toList()
    val afterMatches = NUMBER.findAll(after).toList()
    val sameCount = beforeNums.size == afterMatches.size
    val out = mutableListOf<Segment>()
    var last = 0
    afterMatches.forEachIndexed { i, m ->
        if (m.range.first > last) out += Segment(after.substring(last, m.range.first), false)
        out += Segment(m.value, !sameCount || beforeNums[i] != m.value)
        last = m.range.last + 1
    }
    if (last < after.length) out += Segment(after.substring(last), false)
    return out
}

/** One value when the game agrees on "before", both values otherwise. */
fun mismatchText(change: Change): String? {
    if (change.verification != "mismatch") return null
    val game = change.gameData ?: return null
    return if (game.before == change.before) "En el juego figura ${game.after}"
    else "En el juego figura ${game.before} ⇒ ${game.after}"
}

/** What screen readers announce for the inline verification icon. */
fun verificationAltText(verification: String): String = if (verification == "mismatch") "⚠" else "✓"
