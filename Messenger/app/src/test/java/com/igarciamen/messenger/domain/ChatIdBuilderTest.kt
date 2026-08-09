package com.igarciamen.messenger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatIdBuilderTest {

    @Test
    fun `genera el mismo id independientemente del orden de los uids`() {
        val id1 = ChatIdBuilder.build("uidA", "uidB")
        val id2 = ChatIdBuilder.build("uidB", "uidA")
        assertEquals(id1, id2)
    }

    @Test
    fun `el id contiene ambos uids ordenados alfabeticamente`() {
        val id = ChatIdBuilder.build("uidZ", "uidA")
        assertEquals("uidA_uidZ", id)
    }

    @Test
    fun `el id es estable para el mismo par de uids`() {
        val id1 = ChatIdBuilder.build("abc", "xyz")
        val id2 = ChatIdBuilder.build("abc", "xyz")
        assertEquals(id1, id2)
    }
}