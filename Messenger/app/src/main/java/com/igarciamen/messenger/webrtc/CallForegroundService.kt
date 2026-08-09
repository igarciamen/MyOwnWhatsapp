package com.igarciamen.messenger.webrtc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.igarciamen.messenger.MainActivity

/**
 * Foreground Service que mantiene la llamada activa (audio y, en su caso,
 * vídeo) mientras la pantalla está apagada o la app en segundo plano.
 *
 * Sin este servicio, tras un periodo de inactividad de pantalla Android
 * puede reducir prioridad de CPU o llegar a suspender la captura de
 * audio/vídeo de la app, cortando el sonido de la llamada en curso -- un
 * comportamiento observado de forma reproducible en pruebas reales.
 *
 * Se apoya en dos mecanismos:
 * - startForeground() con una notificación persistente: obliga al sistema
 *   a tratar el proceso como de alta prioridad mientras dure la llamada.
 * - Un WakeLock parcial: evita que la CPU entre en suspensión profunda,
 *   sin mantener la pantalla encendida (PARTIAL_WAKE_LOCK).
 */
class CallForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: AudioManager
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    companion object {
        private const val CHANNEL_ID = "call_foreground_channel"
        private const val NOTIFICATION_ID = 4321
        const val EXTRA_IS_VIDEO_CALL = "extra_is_video_call"

        fun start(context: Context, isVideoCall: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_IS_VIDEO_CALL, isVideoCall)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isVideoCall = intent?.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false) ?: false

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isVideoCall) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        } else 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), serviceType)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        acquireWakeLock()

        // MODE_IN_COMMUNICATION es el modo de audio correcto para llamadas
        // VoIP: ajusta el enrutamiento (altavoz/auricular) y el
        // procesamiento de audio (cancelación de eco, etc.) de forma
        // apropiada para una conversación en tiempo real.
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Messenger:CallWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // Límite de seguridad: 30 minutos como máximo por si algo impidiera liberarlo correctamente.
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Llamada en curso")
            .setContentText("Toca para volver a la llamada")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Llamadas en curso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente mientras hay una llamada activa"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        audioManager.mode = previousAudioMode
    }

    override fun onBind(intent: Intent?): IBinder? = null
}