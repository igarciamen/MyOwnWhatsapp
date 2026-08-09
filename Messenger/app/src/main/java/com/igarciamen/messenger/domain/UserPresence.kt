package com.igarciamen.messenger.domain

data class UserPresence(
    val state: String = "offline",
    val lastChanged: Long = 0L
) {
    val isOnline: Boolean
        get() = state == "online"
}