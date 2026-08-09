package com.igarciamen.messenger.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.igarciamen.messenger.domain.Chat
import com.igarciamen.messenger.domain.ChatIdBuilder
import com.igarciamen.messenger.domain.Message
import com.igarciamen.messenger.service.FcmSender
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
    private val fcmSender: FcmSender
) {

    fun buildChatId(uid1: String, uid2: String): String {
        return ChatIdBuilder.build(uid1, uid2)
    }

    suspend fun getOrCreateChat(currentUserId: String, otherUserId: String): String {
        val chatId = buildChatId(currentUserId, otherUserId)
        val chatRef = firestore.collection("chats").document(chatId)
        val snapshot = chatRef.get().await()

        if (!snapshot.exists()) {
            val newChat = Chat(
                id = chatId,
                participants = listOf(currentUserId, otherUserId)
            )
            chatRef.set(newChat).await()
        }
        return chatId
    }

    fun observeChats(currentUserId: String): Flow<List<Chat>> = callbackFlow {
        val listener = firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val chats = snapshot?.toObjects(Chat::class.java) ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }

    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(chatId: String, senderId: String, text: String) {
        val chatRef = firestore.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document()

        val message = Message(
            id = messageRef.id,
            senderId = senderId,
            text = text,
            timestamp = Timestamp.now(),
            status = "sent"
        )

        val batch = firestore.batch()
        batch.set(messageRef, message)
        batch.update(
            chatRef,
            mapOf(
                "lastMessage" to text,
                "lastMessageTimestamp" to message.timestamp,
                "lastMessageSenderId" to senderId
            )
        )
        batch.commit().await()

        notifyRecipient(chatId, senderId, text)
    }

    private suspend fun notifyRecipient(chatId: String, senderId: String, text: String) {
        val chatSnapshot = firestore.collection("chats").document(chatId).get().await()
        val chat = chatSnapshot.toObject(Chat::class.java) ?: return

        val recipientId = chat.participants.firstOrNull { it != senderId } ?: return
        val recipient = userRepository.getUserById(recipientId) ?: return
        val token = recipient.fcmToken

        Log.d("FCM_DEBUG", "Destinatario: ${recipient.name}, token guardado: $token")

        if (token == null) {
            Log.d("FCM_DEBUG", "El destinatario no tiene token FCM guardado, no se envía notificación")
            return
        }

        val sender = userRepository.getUserById(senderId)
        val senderName = sender?.name ?: "Nuevo mensaje"

        fcmSender.sendNotification(
            toToken = token,
            title = senderName,
            body = text
        )
    }
}