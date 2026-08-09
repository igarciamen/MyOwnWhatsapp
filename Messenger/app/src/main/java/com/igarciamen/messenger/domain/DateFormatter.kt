package com.igarciamen.messenger.domain

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateFormatter {

    private val SPANISH_LOCALE: Locale = Locale.Builder()
        .setLanguage("es")
        .setRegion("ES")
        .build()

    /**
     * Formatea un Timestamp de Firestore para mostrarlo en la lista de chats:
     * - Si es hoy: "14:32"
     * - Si es ayer: "Ayer"
     * - Si es esta semana: nombre del día abreviado ("lun", "mar"...)
     * - En otro caso: "12/07/25"
     */
    fun formatChatTimestamp(timestamp: Timestamp?, now: Calendar = Calendar.getInstance()): String {
        if (timestamp == null) return ""

        val messageCal = Calendar.getInstance().apply { time = timestamp.toDate() }
        val today = now

        return when {
            isSameDay(messageCal, today) ->
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageCal.time)

            isYesterday(messageCal, today) -> "Ayer"

            isSameWeek(messageCal, today) ->
                SimpleDateFormat("EEE", SPANISH_LOCALE).format(messageCal.time)
                    .replaceFirstChar { it.uppercase() }

            else ->
                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(messageCal.time)
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(a: Calendar, b: Calendar): Boolean {
        val yesterday = (b.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(a, yesterday)
    }

    private fun isSameWeek(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.WEEK_OF_YEAR) == b.get(Calendar.WEEK_OF_YEAR)
    }

    /**
     * Formatea el Timestamp de un mensaje individual dentro del chat:
     * - Si es hoy: "14:32"
     * - Si es otro día: "12/07/25 14:32"
     */
    fun formatMessageTimestamp(timestamp: Timestamp?, now: Calendar = Calendar.getInstance()): String {
        if (timestamp == null) return ""

        val messageCal = Calendar.getInstance().apply { time = timestamp.toDate() }

        return if (isSameDay(messageCal, now)) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageCal.time)
        } else {
            SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(messageCal.time)
        }
    }
}