package com.igarciamen.messenger.service

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.igarciamen.messenger.domain.FcmPayloadBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmSender @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val projectId = "messenger-app-3840f"
    private val fcmEndpoint = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
    private val client = OkHttpClient()

    private val scopes = listOf("https://www.googleapis.com/auth/firebase.messaging")

    suspend fun sendNotification(toToken: String, title: String, body: String) {
        withContext(Dispatchers.IO) {
            try {
                val accessToken = getAccessToken()
                Log.d("FCM_DEBUG", "Token OAuth2 obtenido correctamente")
                val json = FcmPayloadBuilder.build(toToken, title, body)

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(fcmEndpoint)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("FCM_DEBUG", "Respuesta FCM: ${response.code} - ${response.body?.string()}")
                }
            } catch (e: Exception) {
                Log.e("FCM_DEBUG", "Error al enviar notificación", e)
            }
        }
    }

    private fun getAccessToken(): String {
        val inputStream = context.assets.open("fcm_service_account.json")
        val credentials = GoogleCredentials
            .fromStream(inputStream)
            .createScoped(scopes)
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}