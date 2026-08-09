package com.igarciamen.messenger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.igarciamen.messenger.data.PresenceRepository
import com.igarciamen.messenger.ui.navigation.MessengerNavGraph
import com.igarciamen.messenger.ui.theme.MessengerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var presenceRepository: PresenceRepository

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermissionIfNeeded()

        setContent {
            MessengerTheme {
                MessengerNavGraph()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Reengancha el tracking de presencia cada vez que la app vuelve a
        // primer plano, incluyendo el caso de reapertura con una sesión ya
        // persistida (el usuario nunca pasa por AuthViewModel.login(), que
        // era el único sitio donde antes se llamaba a
        // startPresenceTracking()). Sin esto, un usuario que cerraba la app
        // deslizándola con la sesión activa y la volvía a abrir quedaba
        // marcado como offline de forma permanente para el resto de
        // usuarios, aunque estuviera usando la app con total normalidad.
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            presenceRepository.startPresenceTracking(currentUserId)
        }
    }

    override fun onStop() {
        super.onStop()
        // setOffline() es una función suspend (espera confirmación real del
        // servidor antes de continuar, ver PresenceRepository). Como onStop()
        // no es una función suspend, la lanzamos en lifecycleScope.
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            lifecycleScope.launch {
                presenceRepository.setOffline(currentUserId)
            }
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}