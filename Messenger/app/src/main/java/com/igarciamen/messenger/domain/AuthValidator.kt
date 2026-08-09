package com.igarciamen.messenger.domain

object AuthValidator {

    fun validateRegister(name: String, email: String, password: String): String? {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            return "Todos los campos son obligatorios"
        }
        if (password.length < 6) {
            return "La contraseña debe tener al menos 6 caracteres"
        }
        return null
    }

    fun validateLogin(email: String, password: String): String? {
        if (email.isBlank() || password.isBlank()) {
            return "Introduce email y contraseña"
        }
        return null
    }
}