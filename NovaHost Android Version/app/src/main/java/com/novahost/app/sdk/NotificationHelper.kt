package com.novahost.app.sdk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.novahost.app.R

/**
 * Trade alerts.
 *
 * These are not "you have a new message" notifications. A fill or a rejection is
 * money moving in an account the user is not looking at, and the common case is
 * a phone face-down on silent. So the channel is deliberately built like an
 * alarm rather than a chat ping.
 *
 * ## Why the channel id carries a version
 *
 * A NotificationChannel is **immutable once created**. Sound, importance and
 * vibration are copied into system settings the first time the app registers
 * it, and every later `createNotificationChannel` call with the same id is a
 * no-op -- the user owns those settings from then on. The original
 * `trade_signals_channel` was created with the default notification sound, so
 * changing the tone is impossible without a new id. [CHANNEL_ID] is therefore
 * versioned, and the old channel is deleted so the user is not left with two
 * "Trade Signals" entries in settings, one of them dead but still holding their
 * preference.
 *
 * Bumping the suffix again is the only way to change these defaults in future,
 * and it resets whatever the user has since chosen -- so do it only for a real
 * defect, not a tweak.
 */
object NotificationHelper {

    /**
     * v3: v1 shipped the default notification tone, and v2 shipped the system
     * ALARM tone because no bundled asset existed yet. `res/raw/trade_alert.wav`
     * now does, and a channel's sound is fixed at creation -- so a device that
     * already registered v2 would keep the generic alarm forever without a new
     * id. This is the bump the note below warns about, spent on the one thing it
     * is worth spending on: the alert no longer sounds like anything else on the
     * phone.
     */
    private const val CHANNEL_ID = "trade_signals_v3"
    private const val CHANNEL_NAME = "Trade alerts"

    /**
     * Every id this channel has ever used. All are deleted on init so a user is
     * never left with several "Trade alerts" entries in system settings, one
     * live and the rest dead but still holding their preferences.
     */
    private val LEGACY_CHANNEL_IDS = listOf("trade_signals_channel", "trade_signals_v2")

    /**
     * Drop an audio file at `res/raw/trade_alert.(ogg|mp3|wav)` and it becomes the
     * alert tone with no code change -- the lookup is by resource name.
     *
     * Until one exists the default ALARM tone is used rather than the default
     * NOTIFICATION tone. That is the point: the notification tone is exactly the
     * one users silence, and it is indistinguishable from every other app.
     */
    private const val CUSTOM_SOUND_RES_NAME = "trade_alert"

    /**
     * Resolves the alert tone, preferring a bundled asset over the system alarm.
     */
    private fun alertSound(context: Context): Uri {
        val id = context.resources.getIdentifier(
            CUSTOM_SOUND_RES_NAME, "raw", context.packageName
        )
        return if (id != 0) {
            Uri.parse("android.resource://${context.packageName}/$id")
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    /**
     * USAGE_ALARM is what carries the sound past a silenced ringer. An alert the
     * user cannot hear is the same as no alert, and unlike a chat app the cost of
     * missing one here is a position they do not know they hold.
     */
    private fun alarmAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    fun init(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Retire every earlier id. Harmless if one was never created.
        LEGACY_CHANNEL_IDS.forEach { old ->
            runCatching { nm.deleteNotificationChannel(old) }
        }

        // Already registered: the user owns these settings now, leave them alone.
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Fills, rejections and anything else your robot does with your money."
            setSound(alertSound(context), alarmAttributes())
            enableVibration(true)
            // Two firm pulses. Distinct from the single buzz of a message.
            vibrationPattern = longArrayOf(0, 260, 130, 260)
            enableLights(true)
            // Requested, not guaranteed -- granting it needs notification policy
            // access, which we do not ask for. Set so that a user who grants it
            // elsewhere gets the behaviour they would expect.
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * The robot is the brand the user bought, so alerts are voiced as the robot
     * acting on its own -- never as a mentor pushing a signal at them.
     */
    private fun robotName(context: Context): String =
        context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
            .getString("display_name", null)
            ?.takeIf { it.isNotBlank() }
            ?: "Your robot"

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.novahost_mark)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // CATEGORY_ALARM keeps the alarm treatment on the notification itself,
            // not just the channel -- it is what Do Not Disturb reads when
            // deciding whether this is interruptive.
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alertSound(context), AudioAttributes.USAGE_ALARM)
            .setVibrate(longArrayOf(0, 260, 130, 260))
            .setAutoCancel(true)

    fun showTradeNotification(context: Context, pair: String, action: String, price: String) {
        init(context)
        val text = "${action.uppercase()} $pair @ $price"
        val n = base(context)
            .setContentTitle("${robotName(context)} executed a trade")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), n)
    }

    /**
     * Shown when a signal arrived but the order was rejected. Users must never be
     * left believing they hold a position that was never opened -- which is why
     * this is as loud as a fill, not quieter.
     */
    fun showTradeFailedNotification(context: Context, pair: String, reason: String) {
        init(context)
        val n = base(context)
            .setContentTitle("${robotName(context)} could not place $pair")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), n)
    }
}
