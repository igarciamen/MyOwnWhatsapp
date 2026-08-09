package com.igarciamen.messenger.domain

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MessageTimestampFormatterTest {

    @Test
    fun `mensaje de hoy muestra solo la hora`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 22, 12, 22, 0)
        }
        val messageTime = now.clone() as Calendar

        val result = DateFormatter.formatMessageTimestamp(
            Timestamp(messageTime.time),
            now
        )

        assertEquals("12:22", result)
    }

    @Test
    fun `mensaje de otro dia muestra fecha y hora`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 22, 10, 0, 0)
        }
        val oldDate = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 5, 9, 15, 0)
        }

        val result = DateFormatter.formatMessageTimestamp(
            Timestamp(oldDate.time),
            now
        )

        assertEquals("05/01/26 09:15", result)
    }

    @Test
    fun `timestamp nulo devuelve cadena vacia`() {
        val result = DateFormatter.formatMessageTimestamp(null)
        assertEquals("", result)
    }
}