package com.igarciamen.messenger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun `validateRegister falla si el nombre esta vacio`() {
        val error = AuthValidator.validateRegister("", "test@messenger.com", "123456")
        assertEquals("Todos los campos son obligatorios", error)
    }

    @Test
    fun `validateRegister falla si el email esta vacio`() {
        val error = AuthValidator.validateRegister("Ismael", "", "123456")
        assertEquals("Todos los campos son obligatorios", error)
    }

    @Test
    fun `validateRegister falla si la password es muy corta`() {
        val error = AuthValidator.validateRegister("Ismael", "test@messenger.com", "123")
        assertEquals("La contraseña debe tener al menos 6 caracteres", error)
    }

    @Test
    fun `validateRegister pasa con datos correctos`() {
        val error = AuthValidator.validateRegister("Ismael", "test@messenger.com", "123456")
        assertNull(error)
    }

    @Test
    fun `validateLogin falla si el email esta vacio`() {
        val error = AuthValidator.validateLogin("", "123456")
        assertEquals("Introduce email y contraseña", error)
    }

    @Test
    fun `validateLogin falla si la password esta vacia`() {
        val error = AuthValidator.validateLogin("test@messenger.com", "")
        assertEquals("Introduce email y contraseña", error)
    }

    @Test
    fun `validateLogin pasa con datos correctos`() {
        val error = AuthValidator.validateLogin("test@messenger.com", "123456")
        assertNull(error)
    }
}