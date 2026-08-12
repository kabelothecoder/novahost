package com.novaedge.app.sdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.novaedge.app.R

object NotificationHelper {
    private const val CHANNEL_ID = "trade_signals_channel"
    private const val CHANNEL_NAME = "Trade Signals"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Alerts for executed trades and signals"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTradeNotification(context: Context, pair: String, action: String, price: String) {
        // Ensure channel is initialized
        init(context)

        // The robot is the brand the user bought -- the notification is voiced as
        // the robot acting on its own, never as a mentor pushing a signal.
        val robotName = context
            .getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
            .getString("display_name", null)
            ?.takeIf { it.isNotBlank() }
            ?: "Your robot"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("$robotName executed a trade")
            .setContentText("${action.uppercase()} $pair @ $price")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    /**
     * Shown when a signal arrived but the order was rejected. Users must never
     * be left believing they hold a position that was never opened.
     */
    fun showTradeFailedNotification(context: Context, pair: String, reason: String) {
        init(context)

        val robotName = context
            .getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
            .getString("display_name", null)
            ?.takeIf { it.isNotBlank() }
            ?: "Your robot"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("$robotName could not place $pair")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
