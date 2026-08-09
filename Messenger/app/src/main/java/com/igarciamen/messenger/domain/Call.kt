package com.igarciamen.messenger.domain

import com.google.firebase.Timestamp

data class Call(
    val id: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val type: String = "audio", // "audio" | "video"
    val status: String = "ringing", // "ringing" | "accepted" | "rejected" | "ended"
    val offer: String? = null,
    val answer: String? = null,
    val createdAt: Timestamp? = null
)