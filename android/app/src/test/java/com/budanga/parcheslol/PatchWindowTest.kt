package com.budanga.parcheslol

import com.budanga.parcheslol.domain.patchWindowDelay
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PatchWindowTest {
    private fun at(s: String) = Instant.parse(s)

    @Test fun dentroDeLaVentanaRevisaCada30Minutos() {
        // Tuesday 2026-11-17, 18:40 UTC.
        assertEquals(Duration.ofMinutes(30), patchWindowDelay(at("2026-11-17T18:40:00Z")))
        // Wednesday 23:50 UTC is still inside.
        assertEquals(Duration.ofMinutes(30), patchWindowDelay(at("2026-11-18T23:50:00Z")))
    }

    @Test fun fueraDeLaVentanaEsperaHastaElProximoMartesOMiercoles() {
        // Tuesday 10:00 UTC → that day at 18:00.
        assertEquals(Duration.ofHours(8), patchWindowDelay(at("2026-11-17T10:00:00Z")))
        // Tuesday 23:59:30 is inside; Wednesday 00:20 UTC → Wednesday 18:00.
        assertEquals(Duration.ofHours(17).plusMinutes(40), patchWindowDelay(at("2026-11-18T00:20:00Z")))
        // Thursday 00:20 UTC → next Tuesday 18:00.
        assertEquals(Duration.ofDays(5).plusHours(17).plusMinutes(40), patchWindowDelay(at("2026-11-19T00:20:00Z")))
    }
}
