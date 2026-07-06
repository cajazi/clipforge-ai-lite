package com.clipforge.ai.core.supabase

import com.clipforge.ai.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

class SupabaseUnavailableException(message: String) : IllegalStateException(message)

/**
 * Supabase REST API client.
 * Uses Retrofit + OkHttp — no extra Supabase SDK dependency needed.
 * Auth header uses the anon key for public access.
 * TODO: Add JWT bearer token once user auth is implemented.
 */
object SupabaseClient {

    var accessTokenProvider: (suspend () -> String?)? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
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
    }

    private val configuredRetrofit: Retrofit by lazy {
        buildRetrofit(SupabaseConfig.currentValidation())
    }

    val retrofit: Retrofit
        get() {
            SupabaseConfig.validationError()?.let { throw SupabaseUnavailableException(it) }
            return configuredRetrofit
        }

    inline fun <reified T> create(): T = create(T::class.java)

    fun <T> create(serviceClass: Class<T>): T =
        create(serviceClass, SupabaseConfig.currentValidation())

    internal fun <T> create(
        serviceClass: Class<T>,
        validation: SupabaseConfigValidation
    ): T {
        validation.error?.let { return unavailableApi(serviceClass, it) }
        return buildRetrofit(validation).create(serviceClass)
    }

    internal fun buildRestBaseUrl(validation: SupabaseConfigValidation): String {
        validation.error?.let { throw SupabaseUnavailableException(it) }
        return validation.restBaseUrl
    }

    private fun buildRetrofit(validation: SupabaseConfigValidation): Retrofit =
        Retrofit.Builder()
            .baseUrl(buildRestBaseUrl(validation))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private fun <T> unavailableApi(serviceClass: Class<T>, message: String): T {
        require(serviceClass.isInterface) { "Supabase API type must be an interface." }
        val handler = InvocationHandler { proxy, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@InvocationHandler handleObjectMethod(proxy, method, args)
            }
            throw SupabaseUnavailableException(message)
        }
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            serviceClass.classLoader,
            arrayOf(serviceClass),
            handler
        ) as T
    }

    private fun handleObjectMethod(proxy: Any, method: Method, args: Array<Any?>?): Any? =
        when (method.name) {
            "toString" -> "UnavailableSupabaseApi(${SupabaseConfig.validationError() ?: "unknown"})"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> null
        }
}
