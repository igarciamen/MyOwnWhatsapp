package com.igarciamen.messenger.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.igarciamen.messenger.data.AuthRepository
import com.igarciamen.messenger.data.AuthResult
import com.igarciamen.messenger.data.PresenceRepository
import com.igarciamen.messenger.data.UserRepository
import com.igarciamen.messenger.domain.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val presenceRepository: PresenceRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isUserLoggedIn(): Boolean = authRepository.currentUserId != null

    fun register(name: String, email: String, password: String) {
        val validationError = AuthValidator.validateRegister(name, email, password)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.register(name, email, password)) {
                is AuthResult.Success -> {
                    authRepository.currentUserId?.let { presenceRepository.startPresenceTracking(it) }
                    userRepository.refreshFcmTokenAfterLogin()
                    _uiState.value = AuthUiState.Success
                }
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(result.message)
            }
        }
    }

    fun login(email: String, password: String) {
        val validationError = AuthValidator.validateLogin(email, password)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    authRepository.currentUserId?.let { presenceRepository.startPresenceTracking(it) }
                    userRepository.refreshFcmTokenAfterLogin()
                    _uiState.value = AuthUiState.Success
                }
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(result.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}