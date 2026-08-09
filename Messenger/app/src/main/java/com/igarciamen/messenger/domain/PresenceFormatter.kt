package com.igarciamen.messenger.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PresenceFormatter {

    fun formatPresence(presence: UserPresence, now: Long = System.currentTimeMillis()): String {
        if (presence.isOnline) return "En línea"
        if (presence.lastChanged == 0L) return "Desconectado"

        val diffMillis = now - presence.lastChanged
        val diffMinutes = diffMillis / 60_000
        val diffHours = diffMinutes / 60

        return when {
            diffMinutes < 1 -> "Últ. vez hace un momento"
            diffMinutes < 60 -> "Últ. vez hace $diffMinutes min"
            diffHours < 24 -> "Últ. vez hace $diffHours h"
            isYesterday(presence.lastChanged, now) -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(presence.lastChanged))
                "Últ. vez ayer a las $time"
            }
            else -> {
                val date = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(presence.lastChanged))
                "Últ. vez el $date"
            }
        }
    }

    private fun isYesterday(timestamp: Long, now: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

        return cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }
}