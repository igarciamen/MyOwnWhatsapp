package com.igarciamen.messenger.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings

object NotificationHelper {

    const val CHANNEL_ID = "messages_channel"
    private const val CHANNEL_NAME = "Mensajes"
    private const val CHANNEL_DESCRIPTION = "Notificaciones de nuevos mensajes de chat"

    fun createNotificationChannel(context: Context) {
        // NotificationChannel solo existe desde Android 8.0 (API 26).
        // En versiones anteriores no es necesario ni posible crearlo.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)

            val soundUri: Uri = Settings.System.DEFAULT_NOTIFICATION_URI
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(soundUri, audioAttributes)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}