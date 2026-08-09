package com.igarciamen.messenger.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.igarciamen.messenger.data.AuthRepository
import com.igarciamen.messenger.data.ChatRepository
import com.igarciamen.messenger.data.PresenceRepository
import com.igarciamen.messenger.domain.Message
import com.igarciamen.messenger.domain.UserPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChatUiState {
    data object Loading : ChatUiState()
    data class Content(val messages: List<Message>) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val presenceRepository: PresenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])
    private val otherUserId: String = checkNotNull(savedStateHandle["otherUserId"])

    val currentUserId: String?
        get() = authRepository.currentUserId

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _otherUserPresence = MutableStateFlow(UserPresence())
    val otherUserPresence: StateFlow<UserPresence> = _otherUserPresence.asStateFlow()

    init {
        observeMessages()
        observePresence()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            try {
                chatRepository.observeMessages(chatId).collect { messages ->
                    _uiState.value = ChatUiState.Content(messages)
                }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error(e.message ?: "Error al cargar los mensajes")
            }
        }
    }

    private fun observePresence() {
        viewModelScope.launch {
            try {
                presenceRepository.observeUserPresence(otherUserId).collect { presence ->
                    _otherUserPresence.value = presence
                }
            } catch (e: Exception) {
                // Si falla la presencia, no rompemos el chat: simplemente no se muestra el estado.
            }
        }
    }

    fun onMessageTextChange(newText: String) {
        _messageText.value = newText
    }

    fun sendMessage() {
        val text = _messageText.value.trim()
        val senderId = currentUserId
        if (text.isBlank() || senderId == null) return

        _messageText.value = ""

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(chatId, senderId, text)
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error(e.message ?: "Error al enviar el mensaje")
            }
        }
    }
}