package com.novaedge.app.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.novaedge.app.NovaEdgeApplication
import com.novaedge.app.R
import kotlinx.coroutines.*

/**
 * NanoBananaService — MT4/MT5 Connection Watcher
 *
 * A foreground service that polls the MetaAPI connection state every 5 seconds.
 * Fires a High-Priority notification if connection drops.
 */
class NanoBananaService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastConnected = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID_FOREGROUND, buildForegroundNotification())
        startWatcher()
        return START_STICKY
    }

    private fun startWatcher() {
        scope.launch {
            while (isActive) {
                checkConnection()
                delay(5_000L)
            }
        }
    }

    private fun checkConnection() {
        // Replace with real MetaAPI connection check:
        // val isConnected = MetaAPIManager.isConnected()
        val isConnected = true // placeholder

        if (lastConnected && !isConnected) {
            fireDisconnectAlert()
        }
        lastConnected = isConnected
    }

    private fun fireDisconnectAlert() {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, NovaEdgeApplication.CHANNEL_ID_WATCHER)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("⚠ Nova Edge — Connection Lost")
            .setContentText("MT4/MT5 terminal connection dropped. Tap to reconnect.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_ALERT, notif)
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, NovaEdgeApplication.CHANNEL_ID_WATCHER)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("Nova Edge Active")
            .setContentText("Monitoring your trading terminal…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID_FOREGROUND = 1001
        const val NOTIF_ID_ALERT      = 1002
    }
}
