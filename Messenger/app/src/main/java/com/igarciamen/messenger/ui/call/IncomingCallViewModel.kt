package com.igarciamen.messenger.ui.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.igarciamen.messenger.data.AuthRepository
import com.igarciamen.messenger.data.CallRepository
import com.igarciamen.messenger.data.UserRepository
import com.igarciamen.messenger.domain.Call
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomingCallViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val callRepository: CallRepository
) : ViewModel() {

    private val _incomingCall = MutableStateFlow<Call?>(null)
    val incomingCall: StateFlow<Call?> = _incomingCall.asStateFlow()

    private val _incomingCallerName = MutableStateFlow<String?>(null)
    val incomingCallerName: StateFlow<String?> = _incomingCallerName.asStateFlow()

    init {
        // IncomingCallViewModel vive a nivel de NavGraph y se instancia una
        // sola vez para toda la vida de la app (necesario para detectar
        // llamadas entrantes desde cualquier pantalla). El bug real: antes
        // este init leía authRepository.currentUserId UNA sola vez; si en
        // ese instante (arranque con LoginScreen visible) todavía no había
        // sesión, el listener de observeIncomingCalls() nunca llegaba a
        // engancharse, y un login posterior no volvía a ejecutar este init
        // -- dejando las llamadas entrantes completamente rotas hasta el
        // siguiente arranque en frío del proceso.
        //
        // Ahora observamos observeAuthState(), que SÍ es reactivo: cada vez
        // que cambia el estado de sesión (login o logout), flatMapLatest
        // cancela el listener anterior y engancha uno nuevo con el uid
        // correcto, o lo desactiva por completo si no hay sesión.
        viewModelScope.launch {
            authRepository.observeAuthState()
                .flatMapLatest { isLoggedIn ->
                    val currentUserId = authRepository.currentUserId
                    if (isLoggedIn && currentUserId != null) {
                        Log.d("INCOMING_CALL_DEBUG", "Reenganchando observeIncomingCalls para uid=$currentUserId")
                        callRepository.observeIncomingCalls(currentUserId)
                    } else {
                        Log.d("INCOMING_CALL_DEBUG", "Sin sesión activa: desactivando observeIncomingCalls")
                        flowOf(null)
                    }
                }
                .collect { call ->
                    Log.d("INCOMING_CALL_DEBUG", "Evento recibido: call=$call")
                    _incomingCall.value = call
                    if (call != null) {
                        _incomingCallerName.value = userRepository.getUserById(call.callerId)?.name
                        Log.d("INCOMING_CALL_DEBUG", "Nombre del emisor resuelto: ${_incomingCallerName.value}")
                    } else {
                        _incomingCallerName.value = null
                    }
                }
        }
    }

    fun dismissIncomingCall() {
        _incomingCall.value = null
    }

    fun rejectIncomingCall() {
        val call = _incomingCall.value ?: return
        viewModelScope.launch { callRepository.rejectCall(call.id) }
        _incomingCall.value = null
    }
}