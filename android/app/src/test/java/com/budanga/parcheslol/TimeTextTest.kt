package com.budanga.parcheslol

import com.budanga.parcheslol.domain.shortDate
import com.budanga.parcheslol.domain.updatedText
import java.time.ZoneId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {
    @Test fun tiempos() {
        val now = Instant.parse("2026-11-23T12:00:00Z")
        assertEquals("Actualizado hace 2 h", updatedText(Instant.parse("2026-11-23T10:00:00Z"), now))
        assertEquals("Actualizado hace 1 min", updatedText(now, now))
        assertEquals("Actualizado hace 1 día", updatedText(Instant.parse("2026-11-22T11:00:00Z"), now))
        assertEquals("Actualizado hace 3 días", updatedText(Instant.parse("2026-11-20T11:00:00Z"), now))
    }

    @Test fun fechaCorta() {
        assertEquals("17 nov", shortDate("2026-11-17T19:00:00.000Z", ZoneId.of("America/Argentina/Buenos_Aires")))
        assertEquals("8 ene", shortDate("2025-01-08T12:00:00Z", ZoneId.of("UTC")))
    }
}
