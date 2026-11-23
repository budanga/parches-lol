package com.budanga.parcheslol

import com.budanga.parcheslol.domain.normalizeForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test fun ignoraAcentosMayusculasYApostrofos() {
        assertEquals("kaisa", normalizeForSearch("Kai'Sa"))
        assertEquals("belveth", normalizeForSearch("Bel’Veth"))
        assertTrue(normalizeForSearch("Nunu y Willump").contains(normalizeForSearch("NUNU")))
        assertEquals("ezreal", normalizeForSearch("Ézreal"))
    }
}
