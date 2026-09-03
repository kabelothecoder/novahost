package com.novahost.app.sdk

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.annotations.SupabaseInternal
import io.ktor.client.plugins.HttpTimeout
import com.novahost.app.BuildConfig

/**
 * The single connection to the NovaHost backend.
 *
 * The `io.github.jan.supabase.*` imports and the `SupabaseClient` type below are
 * the vendor SDK's own names and cannot be renamed -- they are package
 * coordinates, not branding. Everything this app owns says NovaHost, so call
 * sites read `NovaHostBackend.client`.
 */
object NovaHostBackend {
    private val API_URL = BuildConfig.NOVAHOST_API_URL.removeSuffix("/")
    private val API_KEY = BuildConfig.NOVAHOST_API_KEY

    init {
        if (API_URL.isBlank() || API_KEY.isBlank()) {
            throw IllegalStateException("Fatal: NovaHost API URL or key is missing from local.properties. Do NOT fall back to a dummy URL.")
        }
    }

    @OptIn(SupabaseInternal::class)
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = API_URL,
            supabaseKey = API_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
            install(Realtime)
            install(Storage)

            httpConfig {
                // 15s across the board was too tight for a handset on mobile
                // data. Broker-side operations legitimately run past it, and the
                // timeout fired on calls that had already succeeded server-side
                // -- users were told their account failed to link while the link
                // was being written. Slow is acceptable; lying is not.
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 60_000
                }
            }
        }
    }
}
