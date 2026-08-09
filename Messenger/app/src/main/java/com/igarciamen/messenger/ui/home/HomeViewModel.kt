package com.igarciamen.messenger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.igarciamen.messenger.data.AuthRepository
import com.igarciamen.messenger.data.ChatRepository
import com.igarciamen.messenger.data.PresenceRepository
import com.igarciamen.messenger.data.UserRepository
import com.igarciamen.messenger.domain.Chat
import com.igarciamen.messenger.domain.User
import com.igarciamen.messenger.domain.UserPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatWithUser(
    val chat: Chat,
    val otherUser: User?
)

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Content(val chats: List<ChatWithUser>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val presenceRepository: PresenceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeChats()
    }

    private fun observeChats() {
        val currentUserId = authRepository.currentUserId
        if (currentUserId == null) {
            _uiState.value = HomeUiState.Error("No hay usuario autenticado")
            return
        }

        viewModelScope.launch {
            try {
                chatRepository.observeChats(currentUserId).collect { chats ->
                    val chatsWithUser = chats.map { chat ->
                        val otherUserId = chat.participants.firstOrNull { it != currentUserId }
                        val otherUser = otherUserId?.let { userRepository.getUserById(it) }
                        ChatWithUser(chat = chat, otherUser = otherUser)
                    }
                    _uiState.value = HomeUiState.Content(chatsWithUser)
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Error al cargar los chats")
            }
        }
    }

    fun observePresence(uid: String): Flow<UserPresence> {
        return presenceRepository.observeUserPresence(uid)
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.currentUserId?.let { uid ->
                // Con setOffline() ahora suspend, esta línea espera la
                // confirmación real del servidor antes de continuar,
                // garantizando que logout() se ejecute siempre después.
                presenceRepository.setOffline(uid)
            }
            authRepository.logout()
        }
    }
}