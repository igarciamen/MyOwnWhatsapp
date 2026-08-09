package com.igarciamen.messenger.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.igarciamen.messenger.ui.call.CallScreen
import com.igarciamen.messenger.ui.call.IncomingCallViewModel
import com.igarciamen.messenger.ui.chat.ChatScreen
import com.igarciamen.messenger.ui.contacts.ContactsScreen
import com.igarciamen.messenger.ui.home.HomeScreen
import com.igarciamen.messenger.ui.login.AuthViewModel
import com.igarciamen.messenger.ui.login.LoginScreen
import com.igarciamen.messenger.ui.login.RegisterScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val CHAT = "chat/{chatId}/{otherUserId}/{otherUserName}"
    const val CALL = "call/{otherUserId}/{otherUserName}/{callType}?callId={callId}"

    fun chatRoute(chatId: String, otherUserId: String, otherUserName: String): String {
        val encodedName = URLEncoder.encode(otherUserName, "UTF-8")
        return "chat/$chatId/$otherUserId/$encodedName"
    }

    fun callRoute(otherUserId: String, otherUserName: String, callType: String, callId: String? = null): String {
        val encodedName = URLEncoder.encode(otherUserName, "UTF-8")
        val base = "call/$otherUserId/$encodedName/$callType"
        return if (callId != null) "$base?callId=$callId" else base
    }
}

@Composable
fun MessengerNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    incomingCallViewModel: IncomingCallViewModel = hiltViewModel()
) {
    // Si ya existe una sesión de Firebase Auth activa, arrancamos
    // directamente en Home en vez de mostrar el login innecesariamente.
    val startDestination = if (authViewModel.isUserLoggedIn()) Routes.HOME else Routes.LOGIN

    val incomingCall by incomingCallViewModel.incomingCall.collectAsState()
    val incomingCallerName by incomingCallViewModel.incomingCallerName.collectAsState()

    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Routes.REGISTER)
                    }
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onChatClick = { chatId, otherUserId, otherUserName ->
                        navController.navigate(Routes.chatRoute(chatId, otherUserId, otherUserName))
                    },
                    onNewChatClick = {
                        navController.navigate(Routes.CONTACTS)
                    },
                    onLogout = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onChatReady = { chatId, otherUserId, otherUserName ->
                        navController.navigate(Routes.chatRoute(chatId, otherUserId, otherUserName)) {
                            popUpTo(Routes.CONTACTS) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Routes.CHAT,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("otherUserId") { type = NavType.StringType },
                    navArgument("otherUserName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("otherUserName") ?: ""
                val otherUserName = URLDecoder.decode(encodedName, "UTF-8")
                val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""

                ChatScreen(
                    otherUserName = otherUserName,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAudioCallClick = {
                        navController.navigate(Routes.callRoute(otherUserId, otherUserName, "audio"))
                    },
                    onVideoCallClick = {
                        navController.navigate(Routes.callRoute(otherUserId, otherUserName, "video"))
                    }
                )
            }
            composable(
                route = Routes.CALL,
                arguments = listOf(
                    navArgument("otherUserId") { type = NavType.StringType },
                    navArgument("otherUserName") { type = NavType.StringType },
                    navArgument("callType") { type = NavType.StringType },
                    navArgument("callId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("otherUserName") ?: ""
                val otherUserName = URLDecoder.decode(encodedName, "UTF-8")

                CallScreen(
                    otherUserName = otherUserName,
                    onCallEnded = {
                        navController.popBackStack()
                    }
                )
            }
        }

        incomingCall?.let { call ->
            AlertDialog(
                onDismissRequest = { /* No se permite descartar tocando fuera; debe elegir una opción */ },
                title = { Text("Llamada entrante") },
                text = {
                    Text(
                        "${incomingCallerName ?: "Alguien"} te está llamando " +
                                if (call.type == "video") "(videollamada)" else "(audio)"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        navController.navigate(
                            Routes.callRoute(call.callerId, incomingCallerName ?: "Usuario", call.type, call.id)
                        ) {
                            // Limpiamos cualquier pantalla intermedia (Chat, Contacts...) antes
                            // de entrar en la llamada. Esto no es solo una mejora de UX: también
                            // destruye ViewModels con listeners activos (ChatViewModel y sus
                            // observadores de Firestore/presencia) que, en pruebas reales,
                            // competían por recursos justo en el momento crítico de apertura
                            // de cámara/micrófono cuando la llamada se aceptaba estando dentro
                            // de una conversación, provocando que el audio y vídeo remotos no
                            // llegaran a establecerse correctamente.
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                        incomingCallViewModel.dismissIncomingCall()
                    }) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { incomingCallViewModel.rejectIncomingCall() }) {
                        Text("Rechazar")
                    }
                }
            )
        }
    }
}