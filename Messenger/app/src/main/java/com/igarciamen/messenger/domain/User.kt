package com.igarciamen.messenger.domain

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val status: String = "offline",
    val lastSeen: Long = 0L,
    val fcmToken: String? = null
)