package com.igarciamen.messenger.domain

import org.json.JSONObject

object FcmPayloadBuilder {

    /**
     * Construye el cuerpo JSON exacto que espera la API HTTP v1 de FCM
     * para enviar una notificación con datos a un token concreto.
     */
    fun build(toToken: String, title: String, body: String): JSONObject {
        val notification = JSONObject().apply {
            put("title", title)
            put("body", body)
        }
        val data = JSONObject().apply {
            put("title", title)
            put("body", body)
        }

        // Prioridad alta a nivel de Android: sin esto, en dispositivos con
        // la pantalla apagada durante un tiempo (modo Doze), FCM retrasa
        // la entrega hasta la siguiente ventana de mantenimiento del
        // sistema -- en la práctica, hasta que el usuario enciende la
        // pantalla manualmente. Con "high", el mensaje despierta
        // brevemente el dispositivo para entregarse de inmediato, igual
        // que hacen las apps de mensajería comerciales.
        val androidConfig = JSONObject().apply {
            put("priority", "high")
        }

        val message = JSONObject().apply {
            put("token", toToken)
            put("notification", notification)
            put("data", data)
            put("android", androidConfig)
        }
        return JSONObject().apply {
            put("message", message)
        }
    }
}