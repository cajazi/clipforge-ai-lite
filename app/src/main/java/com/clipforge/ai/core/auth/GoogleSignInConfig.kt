package com.clipforge.ai.core.auth

import android.util.Log
import com.clipforge.ai.BuildConfig

const val GOOGLE_WEB_CLIENT_ID_MISSING_MESSAGE =
    "Google Credential Manager setup failed: Web client ID missing."

const val GOOGLE_WEB_CLIENT_ID_MALFORMED_MESSAGE =
    "Google Credential Manager setup failed: OAuth client mismatch."

const val GOOGLE_CLOUD_PROJECT_CONFIG_MESSAGE =
    "Google Credential Manager setup failed: Google Cloud project not configured correctly."

const val GOOGLE_SIGN_IN_CANCELLED_MESSAGE = "Google sign-in cancelled."

const val GOOGLE_ID_TOKEN_NOT_RETURNED_MESSAGE = "Google ID token was not returned."

const val EXPECTED_GOOGLE_WEB_CLIENT_ID =
    "893472260519-smdknd6rldg2bg6df014s61mqh7unf6j.apps.googleusercontent.com"

data class GoogleSignInConfigValidation(
    val webClientId: String,
    val clientIdBlank: Boolean,
    val clientIdMalformed: Boolean,
    val clientIdMatchesExpected: Boolean,
    val error: String?
) {
    val isValid: Boolean get() = error == null
}

object GoogleSignInConfig {
    private const val TAG = "GoogleSignInConfig"

    fun validate(rawWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID): GoogleSignInConfigValidation {
        val webClientId = rawWebClientId.trim()
        val clientIdBlank = webClientId.isBlank()
        val clientIdMalformed = !clientIdBlank && !WEB_CLIENT_ID_PATTERN.matches(webClientId)
        val clientIdMatchesExpected = webClientId == EXPECTED_GOOGLE_WEB_CLIENT_ID
        val error = when {
            clientIdBlank -> GOOGLE_WEB_CLIENT_ID_MISSING_MESSAGE
            clientIdMalformed || !clientIdMatchesExpected -> GOOGLE_WEB_CLIENT_ID_MALFORMED_MESSAGE
            else -> null
        }

        if (BuildConfig.DEBUG) {
            runCatching {
                // Web client ID is public (not a secret); logging it in debug builds is safe
                // and lets us verify the installed BuildConfig value on-device.
                Log.d(
                    TAG,
                    "BuildConfig.GOOGLE_WEB_CLIENT_ID blank=$clientIdBlank " +
                        "equalsExpected=$clientIdMatchesExpected " +
                        "expected=$EXPECTED_GOOGLE_WEB_CLIENT_ID " +
                        "actual=${webClientId.ifBlank { "<blank>" }} " +
                        "clientIdMalformed=$clientIdMalformed"
                )
            }
        }

        return GoogleSignInConfigValidation(
            webClientId = webClientId,
            clientIdBlank = clientIdBlank,
            clientIdMalformed = clientIdMalformed,
            clientIdMatchesExpected = clientIdMatchesExpected,
            error = error
        )
    }

    private val WEB_CLIENT_ID_PATTERN =
        Regex("""^\d+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$""")
}
