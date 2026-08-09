package com.igarciamen.messenger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceFormatterTest {

    @Test
    fun `usuario online muestra En linea`() {
        val presence = UserPresence(state = "online", lastChanged = 0L)
        val result = PresenceFormatter.formatPresence(presence)
        assertEquals("En línea", result)
    }

    @Test
    fun `usuario offline sin lastChanged muestra Desconectado`() {
        val presence = UserPresence(state = "offline", lastChanged = 0L)
        val result = PresenceFormatter.formatPresence(presence)
        assertEquals("Desconectado", result)
    }

    @Test
    fun `desconectado hace menos de un minuto muestra hace un momento`() {
        val now = 1_000_000L
        val presence = UserPresence(state = "offline", lastChanged = now - 30_000L)
        val result = PresenceFormatter.formatPresence(presence, now)
        assertEquals("Últ. vez hace un momento", result)
    }

    @Test
    fun `desconectado hace 5 minutos muestra hace 5 min`() {
        val now = 1_000_000L
        val presence = UserPresence(state = "offline", lastChanged = now - 5 * 60_000L)
        val result = PresenceFormatter.formatPresence(presence, now)
        assertEquals("Últ. vez hace 5 min", result)
    }

    @Test
    fun `desconectado hace 3 horas muestra hace 3 h`() {
        val now = 1_000_000_000L
        val presence = UserPresence(state = "offline", lastChanged = now - 3 * 60 * 60_000L)
        val result = PresenceFormatter.formatPresence(presence, now)
        assertEquals("Últ. vez hace 3 h", result)
    }
}