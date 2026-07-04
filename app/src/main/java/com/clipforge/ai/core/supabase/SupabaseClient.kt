package com.clipforge.ai.core.supabase

import com.clipforge.ai.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Supabase REST API client.
 * Uses Retrofit + OkHttp — no extra Supabase SDK dependency needed.
 * Auth header uses the anon key for public access.
 * TODO: Add JWT bearer token once user auth is implemented.
 */
object SupabaseClient {

    var accessTokenProvider: (suspend () -> String?)? = null

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val accessToken = runBlocking { accessTokenProvider?.invoke() }
                ?.takeIf { it.isNotBlank() }
            val bearer = accessToken ?: SupabaseConfig.SUPABASE_ANON_KEY
            val request = chain.request().newBuilder()
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            redactHeader("apikey")
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(SupabaseConfig.REST_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    inline fun <reified T> create(): T = retrofit.create(T::class.java)
}
