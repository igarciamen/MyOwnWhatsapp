package com.igarciamen.messenger.ui.call

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.igarciamen.messenger.data.AuthRepository
import com.igarciamen.messenger.data.CallRepository
import com.igarciamen.messenger.domain.IceCandidateData
import com.igarciamen.messenger.domain.IceCandidateMapper
import com.igarciamen.messenger.webrtc.CallForegroundService
import com.igarciamen.messenger.webrtc.WebRtcClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import javax.inject.Inject

sealed class CallUiState {
    data object Connecting : CallUiState()
    data object Ringing : CallUiState()
    data object Connected : CallUiState()
    data object Ended : CallUiState()
}

@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val callRepository: CallRepository,
    val webRtcClient: WebRtcClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val otherUserId: String = checkNotNull(savedStateHandle["otherUserId"])
    private val isVideoCall: Boolean = savedStateHandle.get<String>("callType") == "video"
    private val existingCallId: String? = savedStateHandle["callId"]

    private val currentUserId: String
        get() = authRepository.currentUserId ?: ""

    private var callId: String? = existingCallId
    private val isCaller = existingCallId == null
    private var answerCreated = false

    private var sessionId: Long = 0L

    // Bug real detectado: en el lado que INICIA la llamada, WebRTC genera los
    // candidatos ICE locales casi instantáneamente tras crear la oferta
    // (en torno a 100 ms), mientras que callId solo se conoce después de que
    // createCall() termine de escribir en Firestore (una operación de red
    // que puede tardar más que eso). Sin este búfer, los candidatos
    // generados en ese hueco se descartaban silenciosamente (callId aún
    // null), por lo que el otro extremo nunca los recibía y la conexión
    // ICE no llegaba a establecerse nunca en su lado. Este bug era
    // asimétrico: solo afectaba a quien iniciaba la llamada, nunca a quien
    // la recibía (su callId está disponible desde el primer instante).
    private val pendingLocalCandidates = mutableListOf<IceCandidateData>()

    private val _uiState = MutableStateFlow<CallUiState>(CallUiState.Connecting)
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    val localVideoTrack get() = webRtcClient.getLocalVideoTrack()
    val isVideo get() = isVideoCall

    init {
        // Arranca el Foreground Service en cuanto se inicia la pantalla de
        // llamada (no solo al conectar), para mantener la captura de
        // audio/vídeo activa incluso si la pantalla se apaga durante el
        // tramo de "Llamando..."/"Sonando..." antes de que el otro extremo
        // responda.
        CallForegroundService.start(context, isVideoCall)

        webRtcClient.onRemoteStream = { stream: MediaStream ->
            stream.videoTracks.firstOrNull()?.let { _remoteVideoTrack.value = it }
            _uiState.value = CallUiState.Connected
        }
        webRtcClient.onIceCandidate = { candidate: IceCandidate ->
            val data = IceCandidateMapper.toData(candidate)
            val id = callId
            if (id != null) {
                callRepository.addCandidate(id, isCaller, data)
            } else {
                pendingLocalCandidates.add(data)
            }
        }

        sessionId = webRtcClient.initPeerConnection(isVideoCall) {
            if (isCaller) startOutgoingCall() else joinIncomingCall()
        }
    }

    private fun startOutgoingCall() {
        viewModelScope.launch {
            webRtcClient.createOffer { offer ->
                viewModelScope.launch {
                    val newCallId = callRepository.createCall(
                        callerId = currentUserId,
                        calleeId = otherUserId,
                        type = if (isVideoCall) "video" else "audio",
                        offerSdp = offer.description
                    )
                    callId = newCallId

                    // Vaciamos el búfer: enviamos ahora cualquier candidato
                    // que se generó mientras esperábamos a que createCall()
                    // terminara.
                    pendingLocalCandidates.forEach { candidateData ->
                        callRepository.addCandidate(newCallId, isCaller, candidateData)
                    }
                    pendingLocalCandidates.clear()

                    observeCallUpdates(newCallId)
                    observeRemoteCandidates(newCallId, fromCaller = false)
                }
            }
        }
    }

    private fun joinIncomingCall() {
        val id = existingCallId ?: return
        _uiState.value = CallUiState.Connecting
        viewModelScope.launch {
            callRepository.observeCall(id).collect { call ->
                when (call?.status) {
                    "rejected", "ended" -> {
                        webRtcClient.closeConnection(sessionId)
                        _uiState.value = CallUiState.Ended
                        return@collect
                    }
                }

                if (!answerCreated && call?.offer != null) {
                    answerCreated = true
                    webRtcClient.setRemoteDescription(
                        SessionDescription(SessionDescription.Type.OFFER, call.offer)
                    )
                    webRtcClient.createAnswer { answer ->
                        viewModelScope.launch {
                            callRepository.acceptCall(id, answer.description)
                        }
                    }
                    observeRemoteCandidates(id, fromCaller = true)
                }
            }
        }
    }

    private fun observeCallUpdates(id: String) {
        viewModelScope.launch {
            callRepository.observeCall(id).collect { call ->
                when (call?.status) {
                    "accepted" -> {
                        call.answer?.let { answerSdp ->
                            webRtcClient.setRemoteDescription(
                                SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                            )
                        }
                    }
                    "rejected", "ended" -> {
                        webRtcClient.closeConnection(sessionId)
                        _uiState.value = CallUiState.Ended
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun observeRemoteCandidates(id: String, fromCaller: Boolean) {
        viewModelScope.launch {
            callRepository.observeCandidates(id, fromCaller).collect { candidates ->
                candidates.forEach { data ->
                    webRtcClient.addIceCandidate(IceCandidateMapper.toIceCandidate(data))
                }
            }
        }
    }

    fun toggleMic(enabled: Boolean) = webRtcClient.setMicEnabled(enabled)
    fun toggleCamera(enabled: Boolean) = webRtcClient.setCameraEnabled(enabled)
    fun switchCamera() = webRtcClient.switchCamera()

    fun hangUp() {
        callId?.let { id ->
            viewModelScope.launch { callRepository.endCall(id) }
        }
        webRtcClient.closeConnection(sessionId)
        _uiState.value = CallUiState.Ended
        CallForegroundService.stop(context)
    }

    override fun onCleared() {
        super.onCleared()
        webRtcClient.close(sessionId)
        CallForegroundService.stop(context)
    }
}