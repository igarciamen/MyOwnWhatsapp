package com.igarciamen.messenger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FcmPayloadBuilderTest {

    @Test
    fun `el payload contiene el token en la raiz del mensaje`() {
        val json = FcmPayloadBuilder.build("mi-token-123", "Cristobal", "hola")
        val message = json.getJSONObject("message")
        assertEquals("mi-token-123", message.getString("token"))
    }

    @Test
    fun `el payload incluye title y body en notification`() {
        val json = FcmPayloadBuilder.build("token", "Pepita", "buenas")
        val notification = json.getJSONObject("message").getJSONObject("notification")
        assertEquals("Pepita", notification.getString("title"))
        assertEquals("buenas", notification.getString("body"))
    }

    @Test
    fun `el payload incluye title y body tambien en data`() {
        val json = FcmPayloadBuilder.build("token", "Pepita", "buenas")
        val data = json.getJSONObject("message").getJSONObject("data")
        assertEquals("Pepita", data.getString("title"))
        assertEquals("buenas", data.getString("body"))
    }

    @Test
    fun `distintos tokens generan payloads distintos`() {
        val json1 = FcmPayloadBuilder.build("token-A", "X", "Y")
        val json2 = FcmPayloadBuilder.build("token-B", "X", "Y")
        val token1 = json1.getJSONObject("message").getString("token")
        val token2 = json2.getJSONObject("message").getString("token")
        assertEquals("token-A", token1)
        assertEquals("token-B", token2)
    }
}