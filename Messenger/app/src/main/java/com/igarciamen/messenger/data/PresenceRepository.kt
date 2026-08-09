package com.igarciamen.messenger.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.igarciamen.messenger.domain.UserPresence
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceRepository @Inject constructor(
    private val database: FirebaseDatabase
) {

    /**
     * Marca al usuario actual como online y registra en el servidor
     * que, si el cliente se desconecta de forma abrupta, se marque
     * automáticamente como offline. Debe llamarse al iniciar sesión
     * y cada vez que la app vuelve a primer plano (onStart).
     */
    fun startPresenceTracking(uid: String) {
        Log.d("PRESENCE_DEBUG", "startPresenceTracking llamado para uid=$uid")
        val statusRef = database.getReference("status/$uid")
        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d("PRESENCE_DEBUG", "onDataChange .info/connected: isConnected=$isConnected para uid=$uid")
                if (isConnected) {
                    // Red de seguridad: si el cliente se cae sin avisar
                    // (force-stop, sin batería, pérdida de red), el servidor
                    // ejecutará esto automáticamente. Puede tardar hasta 1-2
                    // minutos en detectarse, según la red.
                    statusRef.onDisconnect().setValue(
                        mapOf(
                            "state" to "offline",
                            "lastChanged" to ServerValue.TIMESTAMP
                        )
                    )
                    // Mientras esté conectado, marcamos online ahora mismo:
                    statusRef.setValue(
                        mapOf(
                            "state" to "online",
                            "lastChanged" to ServerValue.TIMESTAMP
                        )
                    ).addOnSuccessListener {
                        Log.d("PRESENCE_DEBUG", "setValue online OK para uid=$uid")
                    }.addOnFailureListener { e ->
                        Log.e("PRESENCE_DEBUG", "setValue online FALLÓ para uid=$uid", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PRESENCE_DEBUG", "onCancelled en .info/connected para uid=$uid: ${error.message}")
            }
        })
    }

    /**
     * Marca explícitamente al usuario como offline. Se llama de forma
     * proactiva cuando la app pasa a segundo plano (onStop) o al cerrar
     * sesión, para no depender únicamente del timeout de onDisconnect().
     *
     * Es una función suspend que espera la confirmación real del servidor
     * (await()) antes de devolver el control. Esto es importante: sin el
     * await(), la llamada era de "disparo y olvido", y en dispositivos con
     * red más lenta (observado en un Xiaomi con restricciones de batería
     * de MIUI) authRepository.logout() podía ejecutarse ANTES de que esta
     * escritura llegara a completarse, invalidando las credenciales a
     * mitad de la operación y provocando un cierre intermitente de la app
     * justo al cerrar sesión.
     */
    suspend fun setOffline(uid: String) {
        database.getReference("status/$uid").setValue(
            mapOf(
                "state" to "offline",
                "lastChanged" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    /**
     * Observa en tiempo real el estado de presencia de un usuario concreto.
     */
    fun observeUserPresence(uid: String): Flow<UserPresence> = callbackFlow {
        val statusRef = database.getReference("status/$uid")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java) ?: "offline"
                val lastChanged = snapshot.child("lastChanged").getValue(Long::class.java) ?: 0L
                trySend(UserPresence(state = state, lastChanged = lastChanged))
            }

            override fun onCancelled(error: DatabaseError) {
                // Cierre silencioso, sin relanzar la excepción: un permiso denegado
                // aquí es un evento esperado (por ejemplo, al cerrar sesión mientras
                // este listener seguía activo en una pantalla), no un error fatal
                // que deba tumbar la aplicación.
                close()
            }
        }

        statusRef.addValueEventListener(listener)
        awaitClose { statusRef.removeEventListener(listener) }
    }
}