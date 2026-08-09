package com.igarciamen.messenger

import android.app.Application
import com.igarciamen.messenger.service.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MessengerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}