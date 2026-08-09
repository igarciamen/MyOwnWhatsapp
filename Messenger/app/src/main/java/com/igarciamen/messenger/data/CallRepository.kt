package com.igarciamen.messenger.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.igarciamen.messenger.domain.Call
import com.igarciamen.messenger.domain.IceCandidateData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private fun callsCollection() = firestore.collection("calls")

    /**
     * Crea un nuevo documento de llamada con la oferta SDP del emisor.
     * Devuelve el ID de la llamada, que se usará en toda la navegación
     * y señalización posterior.
     */
    suspend fun createCall(callerId: String, calleeId: String, type: String, offerSdp: String): String {
        val callRef = callsCollection().document()
        val call = Call(
            id = callRef.id,
            callerId = callerId,
            calleeId = calleeId,
            type = type,
            status = "ringing",
            offer = offerSdp,
            createdAt = Timestamp.now()
        )
        callRef.set(call).await()
        return callRef.id
    }

    /**
     * Guarda la respuesta SDP del receptor y marca la llamada como aceptada.
     */
    suspend fun acceptCall(callId: String, answerSdp: String) {
        callsCollection().document(callId)
            .update(mapOf("answer" to answerSdp, "status" to "accepted"))
            .await()
    }

    suspend fun rejectCall(callId: String) {
        callsCollection().document(callId)
            .update("status", "rejected")
            .await()
    }

    suspend fun endCall(callId: String) {
        callsCollection().document(callId)
            .update("status", "ended")
            .await()
    }

    /**
     * Observa en tiempo real el documento de una llamada concreta:
     * cambios de estado, aparición de la respuesta SDP, etc.
     */
    fun observeCall(callId: String): Flow<Call?> = callbackFlow {
        val listener = callsCollection().document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Call::class.java))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observa llamadas entrantes dirigidas al usuario actual, en estado "ringing".
     * Alimentará una futura pantalla/servicio de llamada entrante.
     */
    fun observeIncomingCalls(currentUserId: String): Flow<Call?> = callbackFlow {
        val listener = callsCollection()
            .whereEqualTo("calleeId", currentUserId)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val call = snapshot?.documents?.firstOrNull()?.toObject(Call::class.java)
                trySend(call)
            }
        awaitClose { listener.remove() }
    }

    fun addCandidate(callId: String, isCaller: Boolean, candidate: IceCandidateData) {
        val subcollection = if (isCaller) "callerCandidates" else "calleeCandidates"
        callsCollection().document(callId)
            .collection(subcollection)
            .add(candidate)
    }

    fun observeCandidates(callId: String, fromCaller: Boolean): Flow<List<IceCandidateData>> = callbackFlow {
        val subcollection = if (fromCaller) "callerCandidates" else "calleeCandidates"
        val listener = callsCollection().document(callId)
            .collection(subcollection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val candidates = snapshot?.toObjects(IceCandidateData::class.java) ?: emptyList()
                trySend(candidates)
            }
        awaitClose { listener.remove() }
    }
}