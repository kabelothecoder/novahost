package com.novaedge.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NovaEdgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_WATCHER,
                "MT Connection Watcher",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when MT4/MT5 connection is lost"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_WATCHER = "metahost_watcher_channel"
    }
}
