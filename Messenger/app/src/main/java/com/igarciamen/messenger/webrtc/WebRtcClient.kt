package com.igarciamen.messenger.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val eglBase: EglBase = EglBase.create()

    // Servidores STUN (Google, gratuitos) + servidores TURN (cuenta propia de
// Metered, plan gratuito de 500 MB/mes, sin tarjeta). Las credenciales
// públicas compartidas de Open Relay Project (openrelayproject/
// openrelayproject) dejaron de funcionar -- Metered exige ahora
// credenciales propias por cuenta, generadas una sola vez desde el panel
// mediante su API REST (nunca desde la app, para no exponer la clave
// secreta). El STUN por sí solo permite conexión directa peer-to-peer en
// redes con NAT "normal" (Wi-Fi doméstico), pero no en redes de datos
// móviles con NAT simétrico/CGNAT del operador, donde el TURN actúa como
// servidor de relé para el tráfico de audio/vídeo.
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
            .setUsername("1d441ee47c6d1fd18a7fe02d")
            .setPassword("n/tDU93zDDy+ME2v")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
            .setUsername("1d441ee47c6d1fd18a7fe02d")
            .setPassword("n/tDU93zDDy+ME2v")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
            .setUsername("1d441ee47c6d1fd18a7fe02d")
            .setPassword("n/tDU93zDDy+ME2v")
            .createIceServer(),
        PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
            .setUsername("1d441ee47c6d1fd18a7fe02d")
            .setPassword("n/tDU93zDDy+ME2v")
            .createIceServer()
    )

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var activeSessionId = 0L
    private val sessionCounter = AtomicLong(0)

    var onRemoteStream: ((MediaStream) -> Unit)? = null
    var onIceCandidate: ((IceCandidate) -> Unit)? = null

    fun initPeerConnection(isVideoCall: Boolean, onReady: () -> Unit): Long {
        val sessionId = sessionCounter.incrementAndGet()
        activeSessionId = sessionId
        Log.d("WEBRTC_DEBUG", "initPeerConnection: sessionId=$sessionId isVideoCall=$isVideoCall")

        forceClose()

        val delayMs = if (isVideoCall) 300L else 0L
        Handler(Looper.getMainLooper()).postDelayed({
            if (activeSessionId != sessionId) {
                Log.d("WEBRTC_DEBUG", "initPeerConnection: sessionId=$sessionId obsoleto, abortando setup")
                return@postDelayed
            }
            setupPeerConnectionInternal(isVideoCall)
            onReady()
        }, delayMs)

        return sessionId
    }

    private fun setupPeerConnectionInternal(isVideoCall: Boolean) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d("WEBRTC_DEBUG", "onIceCandidate (local): sdp=${candidate.sdp}")
                    onIceCandidate?.invoke(candidate)
                }

                override fun onIceCandidateError(event: org.webrtc.IceCandidateErrorEvent) {
                    Log.e(
                        "WEBRTC_DEBUG",
                        "onIceCandidateError: address=${event.address} port=${event.port} " +
                                "url=${event.url} errorCode=${event.errorCode} errorText=${event.errorText}"
                    )
                }

                override fun onAddStream(stream: MediaStream) {
                    Log.d("WEBRTC_DEBUG", "onAddStream (legacy): audioTracks=${stream.audioTracks.size} videoTracks=${stream.videoTracks.size}")
                    onRemoteStream?.invoke(stream)
                }
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d("WEBRTC_DEBUG", "onSignalingChange: $state")
                }
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d("WEBRTC_DEBUG", "onIceConnectionChange: $state")
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d("WEBRTC_DEBUG", "onIceConnectionReceivingChange: $receiving")
                }
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d("WEBRTC_DEBUG", "onIceGatheringChange: $state")
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {
                    Log.d("WEBRTC_DEBUG", "onRenegotiationNeeded")
                }
                override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out MediaStream>?) {
                    val stream = streams?.firstOrNull()
                    Log.d("WEBRTC_DEBUG", "onAddTrack: audioTracks=${stream?.audioTracks?.size} videoTracks=${stream?.videoTracks?.size}")
                    stream?.let { onRemoteStream?.invoke(it) }
                }
            }
        )

        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", localAudioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))

        if (isVideoCall) {
            videoCapturer = createCameraCapturer()
            videoCapturer?.let { capturer ->
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                localVideoSource = peerConnectionFactory.createVideoSource(false)
                capturer.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
                capturer.startCapture(1280, 720, 30)

                localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", localVideoSource)
                peerConnection?.addTrack(localVideoTrack, listOf("local_stream"))
            }
        }
    }

    fun getLocalVideoTrack(): VideoTrack? = localVideoTrack

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return deviceNames.firstOrNull()?.let { enumerator.createCapturer(it, null) }
    }

    fun createOffer(onSuccess: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                Log.d("WEBRTC_DEBUG", "createOffer onCreateSuccess")
                peerConnection?.setLocalDescription(SdpObserverAdapter(), description)
                onSuccess(description)
            }
            override fun onCreateFailure(error: String?) {
                Log.e("WEBRTC_DEBUG", "createOffer onCreateFailure: $error")
            }
        }, constraints)
    }

    fun createAnswer(onSuccess: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                Log.d("WEBRTC_DEBUG", "createAnswer onCreateSuccess")
                peerConnection?.setLocalDescription(SdpObserverAdapter(), description)
                onSuccess(description)
            }
            override fun onCreateFailure(error: String?) {
                Log.e("WEBRTC_DEBUG", "createAnswer onCreateFailure: $error")
            }
        }, constraints)
    }

    fun setRemoteDescription(description: SessionDescription) {
        Log.d("WEBRTC_DEBUG", "setRemoteDescription: type=${description.type}")
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.d("WEBRTC_DEBUG", "setRemoteDescription onSetSuccess (type=${description.type})")
            }
            override fun onSetFailure(error: String?) {
                Log.e("WEBRTC_DEBUG", "setRemoteDescription onSetFailure: $error")
            }
        }, description)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        Log.d("WEBRTC_DEBUG", "addIceCandidate (remoto): sdp=${candidate.sdp}")
        peerConnection?.addIceCandidate(candidate)
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun closeConnection(sessionId: Long) {
        if (sessionId != activeSessionId) {
            Log.d("WEBRTC_DEBUG", "closeConnection ignorado: sessionId=$sessionId ya no es activeSessionId=$activeSessionId")
            return
        }
        Log.d("WEBRTC_DEBUG", "closeConnection: sessionId=$sessionId")
        forceCloseConnection()
    }

    fun close(sessionId: Long) {
        if (sessionId != activeSessionId) {
            Log.d("WEBRTC_DEBUG", "close ignorado: sessionId=$sessionId ya no es activeSessionId=$activeSessionId")
            return
        }
        Log.d("WEBRTC_DEBUG", "close: sessionId=$sessionId")
        forceClose()
    }

    private fun forceCloseConnection() {
        peerConnection?.close()
        peerConnection = null
    }

    private fun forceReleaseVideoResources() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        disposeLocalMedia()
    }

    private fun forceClose() {
        forceCloseConnection()
        forceReleaseVideoResources()
    }

    private fun disposeLocalMedia() {
        localAudioTrack?.dispose()
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        localVideoSource?.dispose()
        localVideoSource = null
        videoCapturer = null
        surfaceTextureHelper = null
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}