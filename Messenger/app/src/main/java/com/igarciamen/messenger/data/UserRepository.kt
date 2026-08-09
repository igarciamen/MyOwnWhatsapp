package com.igarciamen.messenger.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.igarciamen.messenger.domain.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    fun observeUsers(currentUserId: String): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val users = snapshot?.toObjects(User::class.java)
                    ?.filter { it.uid != currentUserId }
                    ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getUserById(uid: String): User? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)
    }

    suspend fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d("FCM_DEBUG", "No hay usuario autenticado todavía, token NO guardado")
            return
        }
        firestore.collection("users").document(uid)
            .update("fcmToken", token)
            .await()
        Log.d("FCM_DEBUG", "Token guardado en Firestore para uid=$uid")
    }

    /**
     * Se llama justo después de un login/registro con éxito, para garantizar
     * que el token FCM quede guardado incluso si onNewToken() se disparó
     * antes de que hubiera sesión activa (caso muy habitual tras instalar
     * la app o borrar sus datos).
     */
    suspend fun refreshFcmTokenAfterLogin() {
        Log.d("FCM_DEBUG", "refreshFcmTokenAfterLogin: iniciando solicitud de token...")
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d("FCM_DEBUG", "refreshFcmTokenAfterLogin: token obtenido correctamente")
            updateFcmToken(token)
        } catch (e: Exception) {
            Log.e("FCM_DEBUG", "No se pudo refrescar el token FCM tras login", e)
        }
    }
}