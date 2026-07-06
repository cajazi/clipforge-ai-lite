package com.clipforge.ai.core.auth

import android.util.Log
import com.clipforge.ai.BuildConfig

const val GOOGLE_WEB_CLIENT_ID_MISSING_MESSAGE =
    "Google sign-in is not configured. Add GOOGLE_WEB_CLIENT_ID to local.properties."

const val GOOGLE_WEB_CLIENT_ID_MALFORMED_MESSAGE =
    "Google sign-in config is invalid. GOOGLE_WEB_CLIENT_ID must be the Web OAuth client ID."

data class GoogleSignInConfigValidation(
    val webClientId: String,
    val clientIdBlank: Boolean,
    val clientIdMalformed: Boolean,
    val error: String?
) {
    val isValid: Boolean get() = error == null
}

object GoogleSignInConfig {
    private const val TAG = "GoogleSignInConfig"

    fun validate(rawWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID): GoogleSignInConfigValidation {
        val webClientId = rawWebClientId.trim()
        val clientIdBlank = webClientId.isBlank()
        val clientIdMalformed = !clientIdBlank && !webClientId.endsWith(".apps.googleusercontent.com")
        val error = when {
            clientIdBlank -> GOOGLE_WEB_CLIENT_ID_MISSING_MESSAGE
            clientIdMalformed -> GOOGLE_WEB_CLIENT_ID_MALFORMED_MESSAGE
            else -> null
        }

        if (BuildConfig.DEBUG) {
            runCatching {
                // Web client ID is public (not a secret); logging it in debug builds is safe
                // and lets us verify the installed BuildConfig value on-device.
                Log.d(
                    TAG,
                    "clientIdBlank=$clientIdBlank clientIdMalformed=$clientIdMalformed " +
                        "webClientId=${webClientId.ifBlank { "<blank>" }}"
                )
            }
        }

        return GoogleSignInConfigValidation(
            webClientId = webClientId,
            clientIdBlank = clientIdBlank,
            clientIdMalformed = clientIdMalformed,
            error = error
        )
    }
}
