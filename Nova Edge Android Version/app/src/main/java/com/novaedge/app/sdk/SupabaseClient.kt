package com.novaedge.app.sdk

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.annotations.SupabaseInternal
import io.ktor.client.plugins.HttpTimeout
import com.novaedge.app.BuildConfig

object SupabaseSetup {
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL.removeSuffix("/")
    private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    init {
        if (SUPABASE_URL.isBlank() || SUPABASE_ANON_KEY.isBlank()) {
            throw IllegalStateException("Fatal: Supabase URL or Anon Key is missing from local.properties. Do NOT fall back to a dummy URL.")
        }
    }

    @OptIn(SupabaseInternal::class)
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
            install(Realtime)
            install(Storage)

            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15000 // 15 seconds
                    connectTimeoutMillis = 15000 // 15 seconds
                    socketTimeoutMillis = 15000  // 15 seconds
                }
            }
        }
    }
}

