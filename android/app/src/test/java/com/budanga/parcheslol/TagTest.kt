package com.budanga.parcheslol

import com.budanga.parcheslol.domain.TagRole
import com.budanga.parcheslol.domain.leadingMarker
import com.budanga.parcheslol.domain.tagRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagTest {
    @Test fun colorSegunLaPalabra() {
        assertEquals(TagRole.NEW, tagRole("NUEVO"))
        assertEquals(TagRole.NEW, tagRole("NUEVA"))
        assertEquals(TagRole.REMOVED, tagRole("ELIMINADO"))
        assertEquals(TagRole.REMOVED, tagRole("ELIMINADA"))
        assertEquals(TagRole.CHANGED, tagRole("REDISEÑADO"))
        assertEquals(TagRole.CHANGED, tagRole("ACTUALIZADO"))
    }

    @Test fun marcadorAlPrincipioDeLaLinea() {
        assertEquals("NUEVO", leadingMarker("NUEVO: Ahora obtienes 10 de Velocidad"))
        assertEquals("ELIMINADO", leadingMarker("ELIMINADO Único - Fuego Coordinado"))
        assertNull(leadingMarker("Daño: 80 ⇒ 90"))
    }
}
