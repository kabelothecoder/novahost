package com.novahost.app.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.novahost.app.NovaHostApplication
import com.novahost.app.R
import com.novahost.app.sdk.MetaAPIManager
import kotlinx.coroutines.*

/**
 * NanoBananaService -- broker connection watcher.
 *
 * Raises a high-priority notification when a previously-linked trading account
 * drops, so a user whose terminal goes offline finds out before a signal is
 * missed rather than afterwards.
 *
 * It observes [MetaAPIManager.isConnected], which is owned by `probeLinkStatus`.
 * It does not probe on its own -- a watcher that forced its own connection
 * checks would fight the manager for the same state.
 */
class NanoBananaService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Seeded false to match [MetaAPIManager.isConnected]'s initial value.
     *
     * This used to be `true` while the flow starts `false`, and the check itself
     * was a hardcoded `val isConnected = true`. Two bugs cancelling out: the
     * placeholder meant the watcher could never fire at all, and the moment it
     * was wired to real state that `true` seed would have fired a "Connection
     * Lost" alert on every launch on every handset that had not linked a broker
     * yet -- i.e. most of them.
     */
    private var lastConnected = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID_FOREGROUND, buildForegroundNotification())
        startWatcher()
        return START_STICKY
    }

    private fun startWatcher() {
        scope.launch {
            // Collecting the StateFlow replaces a 5-second polling loop that ran
            // for the life of the process and re-read a constant. A StateFlow
            // emits its current value on subscribe and then only on change, so
            // an idle handset does no work here at all.
            MetaAPIManager.isConnected.collect { connected ->
                val dropped = lastConnected && !connected

                // A probe in flight is not a disconnection. Without this, the
                // transient false that probeLinkStatus writes while it re-checks
                // would alert the user every time the app resumed.
                if (dropped && !MetaAPIManager.isProbingLink.value) {
                    fireDisconnectAlert()
                }
                lastConnected = connected
            }
        }
    }

    private fun fireDisconnectAlert() {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, NovaHostApplication.CHANNEL_ID_WATCHER)
            .setSmallIcon(R.drawable.novahost_mark)
            .setContentTitle("\u26a0 NovaHost -- Connection Lost")
            .setContentText("Your trading account disconnected. Tap to reconnect.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_ALERT, notif)
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, NovaHostApplication.CHANNEL_ID_WATCHER)
            .setSmallIcon(R.drawable.novahost_mark)
            .setContentTitle("NovaHost Active")
            .setContentText("Watching your trading connection\u2026")
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
