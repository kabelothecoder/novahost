package com.novahost.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Base64
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.map.Mapper
import coil.request.Options
import java.nio.ByteBuffer

class NovaHostApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * The app-wide image loader, taught to read `data:` URIs.
     *
     * The portal stores robot avatars inline in `expert_advisors.avatar_url` as
     * base64 JPEG rather than uploading them and saving a link, so what reaches
     * the app is a megabyte-long `data:image/jpeg;base64,...` string. Coil ships
     * fetchers for http, file, content and resource -- none of them for data
     * URIs -- so every AsyncImage in the app was failing silently and falling
     * back to the bundled art.
     *
     * Registering the mapper here rather than fixing each call site means the
     * dashboard, the floating avatar, the scanner and the pulse notification all
     * get it, including any avatar added later.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(DataUriMapper(), String::class.java) }
            .crossfade(true)
            .build()

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

/**
 * Decodes `data:image/...;base64,...` into bytes Coil can already handle.
 *
 * Returns null for anything else, which leaves ordinary http/file models to the
 * fetchers that own them.
 */
private class DataUriMapper : Mapper<String, ByteBuffer> {
    override fun map(data: String, options: Options): ByteBuffer? {
        if (!data.startsWith("data:image/")) return null
        val comma = data.indexOf(',')
        if (comma < 0) return null
        return try {
            ByteBuffer.wrap(Base64.decode(data.substring(comma + 1), Base64.DEFAULT))
        } catch (e: IllegalArgumentException) {
            // A truncated or malformed upload must not take the screen down.
            null
        }
    }
}
