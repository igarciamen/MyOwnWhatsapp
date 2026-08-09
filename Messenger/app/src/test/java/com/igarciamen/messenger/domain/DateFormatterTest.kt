package com.igarciamen.messenger.domain

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date

class DateFormatterTest {

    @Test
    fun `mensaje de hoy muestra la hora`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 18, 14, 32, 0)
        }
        val messageTime = now.clone() as Calendar

        val result = DateFormatter.formatChatTimestamp(
            Timestamp(messageTime.time),
            now
        )

        assertEquals("14:32", result)
    }

    @Test
    fun `mensaje de ayer muestra Ayer`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 18, 10, 0, 0)
        }
        val yesterday = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val result = DateFormatter.formatChatTimestamp(
            Timestamp(yesterday.time),
            now
        )

        assertEquals("Ayer", result)
    }

    @Test
    fun `mensaje antiguo muestra fecha completa`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 18, 10, 0, 0)
        }
        val oldDate = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 5, 9, 0, 0)
        }

        val result = DateFormatter.formatChatTimestamp(
            Timestamp(oldDate.time),
            now
        )

        assertEquals("05/01/25", result)
    }

    @Test
    fun `timestamp nulo devuelve cadena vacia`() {
        val result = DateFormatter.formatChatTimestamp(null)
        assertEquals("", result)
    }
}