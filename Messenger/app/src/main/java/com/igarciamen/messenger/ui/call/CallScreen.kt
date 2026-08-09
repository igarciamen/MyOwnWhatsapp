package com.igarciamen.messenger.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    otherUserName: String,
    onCallEnded: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val requiredPermissions = if (viewModel.isVideo) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (!permissionsGranted) {
            onCallEnded()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    if (permissionsGranted) {
        CallScreenContent(
            otherUserName = otherUserName,
            onCallEnded = onCallEnded,
            viewModel = viewModel
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Esperando permisos de cámara/micrófono...", color = Color.White)
        }
    }
}

@Composable
private fun CallScreenContent(
    otherUserName: String,
    onCallEnded: () -> Unit,
    viewModel: CallViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()

    var micEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(viewModel.isVideo) }

    LaunchedEffect(uiState) {
        if (uiState is CallUiState.Ended) {
            onCallEnded()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (viewModel.isVideo) {
            remoteVideoTrack?.let { track ->
                RemoteVideoView(track = track, eglBaseContext = viewModel.webRtcClient.eglBase.eglBaseContext)
            }

            viewModel.localVideoTrack?.let { track ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(120.dp, 160.dp)
                ) {
                    LocalVideoView(track = track, eglBaseContext = viewModel.webRtcClient.eglBase.eglBaseContext)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        ) {
            Text(
                text = otherUserName,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = when (uiState) {
                    is CallUiState.Connecting -> "Conectando..."
                    is CallUiState.Ringing -> "Llamando..."
                    is CallUiState.Connected -> "En llamada"
                    is CallUiState.Ended -> "Llamada finalizada"
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CallControlButton(
                icon = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                onClick = {
                    micEnabled = !micEnabled
                    viewModel.toggleMic(micEnabled)
                }
            )
            Spacer(modifier = Modifier.width(24.dp))

            if (viewModel.isVideo) {
                CallControlButton(
                    icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    onClick = {
                        cameraEnabled = !cameraEnabled
                        viewModel.toggleCamera(cameraEnabled)
                    }
                )
                Spacer(modifier = Modifier.width(24.dp))

                CallControlButton(
                    icon = Icons.Default.Cameraswitch,
                    onClick = { viewModel.switchCamera() }
                )
                Spacer(modifier = Modifier.width(24.dp))
            }

            CallControlButton(
                icon = Icons.Default.CallEnd,
                backgroundColor = Color.Red,
                onClick = { viewModel.hangUp() }
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color = Color.White.copy(alpha = 0.2f),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(backgroundColor, shape = androidx.compose.foundation.shape.CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun RemoteVideoView(track: VideoTrack, eglBaseContext: org.webrtc.EglBase.Context) {
    val renderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                track.addSink(this)
                renderer.value = this
            }
        }
    )

    DisposableEffect(track) {
        onDispose {
            renderer.value?.let { r ->
                track.removeSink(r)
                r.release()
            }
        }
    }
}

@Composable
private fun LocalVideoView(track: VideoTrack, eglBaseContext: org.webrtc.EglBase.Context) {
    val renderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(true)
                // Clave: sin esto, este SurfaceView (pequeño, en primer plano)
                // queda oculto detrás de la capa de hardware del SurfaceView
                // remoto a pantalla completa.
                setZOrderMediaOverlay(true)
                track.addSink(this)
                renderer.value = this
            }
        }
    )

    DisposableEffect(track) {
        onDispose {
            renderer.value?.let { r ->
                track.removeSink(r)
                r.release()
            }
        }
    }
}