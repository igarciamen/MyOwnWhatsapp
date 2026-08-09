package com.igarciamen.messenger.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.igarciamen.messenger.data.AuthRepository
import com.igarciamen.messenger.data.ChatRepository
import com.igarciamen.messenger.data.UserRepository
import com.igarciamen.messenger.domain.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ContactsUiState {
    data object Loading : ContactsUiState()
    data class Content(val users: List<User>) : ContactsUiState()
    data class Error(val message: String) : ContactsUiState()
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        observeUsers()
    }

    private fun observeUsers() {
        val currentUserId = authRepository.currentUserId
        if (currentUserId == null) {
            _uiState.value = ContactsUiState.Error("No hay usuario autenticado")
            return
        }

        viewModelScope.launch {
            userRepository.observeUsers(currentUserId).collect { users ->
                _uiState.value = ContactsUiState.Content(users)
            }
        }
    }

    fun startChatWith(otherUser: User, onChatReady: (chatId: String, otherUserId: String, otherUserName: String) -> Unit) {
        val currentUserId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            val chatId = chatRepository.getOrCreateChat(currentUserId, otherUser.uid)
            onChatReady(chatId, otherUser.uid, otherUser.name)
        }
    }
}