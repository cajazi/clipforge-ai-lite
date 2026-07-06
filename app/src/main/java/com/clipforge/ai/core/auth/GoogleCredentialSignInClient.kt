package com.clipforge.ai.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.clipforge.ai.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

private const val GOOGLE_SIGN_IN_TAG = "GoogleSignIn"

sealed class GoogleCredentialResult {
    data class Success(val idToken: String, val nonce: String) : GoogleCredentialResult()
    object Cancelled : GoogleCredentialResult()
    data class Failure(val message: String) : GoogleCredentialResult()
}

class GoogleCredentialSignInClient(
    private val context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context)
) {
    suspend fun signIn(): GoogleCredentialResult {
        val config = GoogleSignInConfig.validate()
        config.error?.let { return GoogleCredentialResult.Failure(it) }

        val nonce = GoogleNonce.create()
        val signInOption = GetSignInWithGoogleOption.Builder(config.webClientId)
            .setNonce(nonce.sha256Hex)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        debug(
            "native prompt requested clientIdConfigured=true " +
                "clientIdEqualsExpected=${config.clientIdMatchesExpected} " +
                "serverClientId=${config.webClientId} noncePresent=true hashedNonceSent=true"
        )
        return try {
            val response = credentialManager.getCredential(
                context = context,
                request = request
            )
            parseGoogleCredentialResult(response.credential, nonce.raw, ::debug)
        } catch (e: GetCredentialCancellationException) {
            debugCredentialException(e)
            GoogleCredentialResult.Cancelled
        } catch (e: GetCredentialException) {
            debugCredentialException(e)
            GoogleCredentialResult.Failure(credentialManagerFailureMessage(e))
        } catch (e: IllegalArgumentException) {
            debug(
                "credential exception class=${e.javaClass.simpleName} " +
                    "message=${sanitizeCredentialDetail(e.message.orEmpty()).take(200)}"
            )
            GoogleCredentialResult.Failure(
                "Google Credential Manager setup failed: invalid sign-in request configuration."
            )
        }
    }

    private fun debugCredentialException(e: GetCredentialException) {
        val details = credentialExceptionDetails(e)
        debug(
            "credential exception class=${e.javaClass.simpleName} type=${e.type} " +
                "statusCode=${credentialStatusCode(details) ?: "none"} " +
                "message=${sanitizeCredentialDetail(e.message.orEmpty()).take(200)} " +
                "customData=${customExceptionData(e)}"
        )
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) runCatching { Log.d(GOOGLE_SIGN_IN_TAG, message) }
    }
}

// Maps Credential Manager failures to messages that identify the failing layer
// (device account, Play services, Google Cloud OAuth config) without exposing tokens.
internal fun credentialManagerFailureMessage(e: GetCredentialException): String {
    val details = credentialExceptionDetails(e).lowercase()
    return when {
        details.contains("web client") && details.contains("missing") ->
            GOOGLE_WEB_CLIENT_ID_MISSING_MESSAGE
        e is NoCredentialException || details.contains("no credential") ->
            "Google Credential Manager setup failed: no matching Google credential. " +
                "Check the Android OAuth client (package name + SHA-1) and the device Google account."
        e is GetCredentialProviderConfigurationException ->
            "Google Credential Manager setup failed: Google Play services credential provider " +
                "is unavailable or out of date on this device."
        e is GetCredentialInterruptedException ->
            "Google sign-in was interrupted. Please try again."
        details.contains("developer console") ||
            details.contains("28444") ->
            GOOGLE_CLOUD_PROJECT_CONFIG_MESSAGE
        details.contains("developer error") ||
            details.contains("[10]") ||
            details.contains("10:") ||
            details.contains("client id") ||
            details.contains("client_id") ->
            "Google Credential Manager setup failed: OAuth client mismatch. " +
                "Check the Google Cloud project configuration."
        details.contains("network") ->
            "Google Credential Manager setup failed: network error while contacting Google."
        else ->
            "Google Credential Manager setup failed. (${e.javaClass.simpleName})"
    }
}

internal fun parseGoogleCredentialResult(
    credential: Credential,
    nonce: String,
    debug: (String) -> Unit = {}
): GoogleCredentialResult {
    val credentialType = credential.type
    val credentialClass = credential::class.java.name
    debug("credential result type=$credentialType class=$credentialClass")
    if (
        credential !is CustomCredential ||
        credentialType !in GOOGLE_ID_TOKEN_CREDENTIAL_TYPES
    ) {
        debug("credential result idTokenReceived=false type=$credentialType class=$credentialClass")
        return GoogleCredentialResult.Failure(
            "Google Credential Manager setup failed: unexpected credential type."
        )
    }

    return try {
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val idTokenReceived = idToken.isNotBlank()
        debug(
            "credential result idTokenReceived=$idTokenReceived " +
                "type=$credentialType class=$credentialClass"
        )
        if (idTokenReceived) {
            GoogleCredentialResult.Success(idToken = idToken, nonce = nonce)
        } else {
            GoogleCredentialResult.Failure(GOOGLE_ID_TOKEN_NOT_RETURNED_MESSAGE)
        }
    } catch (e: GoogleIdTokenParsingException) {
        debug(
            "credential exception class=${e.javaClass.simpleName} " +
                "message=${sanitizeCredentialDetail(e.message.orEmpty()).take(200)} idTokenReceived=false"
        )
        GoogleCredentialResult.Failure(GOOGLE_ID_TOKEN_NOT_RETURNED_MESSAGE)
    } catch (e: IllegalArgumentException) {
        debug(
            "credential exception class=${e.javaClass.simpleName} " +
                "message=${sanitizeCredentialDetail(e.message.orEmpty()).take(200)} idTokenReceived=false"
        )
        GoogleCredentialResult.Failure(GOOGLE_ID_TOKEN_NOT_RETURNED_MESSAGE)
    }
}

private val GOOGLE_ID_TOKEN_CREDENTIAL_TYPES = setOf(
    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
)

internal fun credentialExceptionDetails(e: GetCredentialException): String =
    "${e.type} ${e.message.orEmpty()}"

internal fun credentialStatusCode(details: String): String? {
    val match = CREDENTIAL_STATUS_CODE.find(details) ?: return null
    return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
}

private fun customExceptionData(e: GetCredentialException): String {
    if (e !is GetCredentialCustomException) return "none"
    return "customType=${sanitizeCredentialDetail(e.type)} " +
        "customMessage=${sanitizeCredentialDetail(e.message.orEmpty()).take(200)}"
}

private fun sanitizeCredentialDetail(value: String): String =
    value.replace(CREDENTIAL_SENSITIVE_FIELD) { match ->
        "${match.groupValues[1]}=<redacted>"
    }.replace(CREDENTIAL_JWT_LIKE_VALUE, "<redacted-jwt>")

private val CREDENTIAL_STATUS_CODE =
    Regex("""(?i)(?:\[(-?\d+)]|\b(?:status(?:\s+code)?|statuscode|code)\s*[:=]\s*(-?\d+))""")

private val CREDENTIAL_SENSITIVE_FIELD =
    Regex("(?i)\\b(access_token|refresh_token|id_token|password|apikey|authorization|client_secret)\\s*[:=]\\s*\\S+")

private val CREDENTIAL_JWT_LIKE_VALUE =
    Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
