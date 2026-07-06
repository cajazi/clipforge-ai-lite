package com.clipforge.ai.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
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

        debug("native prompt requested clientIdConfigured=true noncePresent=true hashedNonceSent=true")
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
            debug("credential exception class=${e.javaClass.simpleName} message=${e.message.orEmpty().take(160)}")
            GoogleCredentialResult.Failure(
                "Google Credential Manager setup failed: invalid sign-in request configuration."
            )
        }
    }

    private fun debugCredentialException(e: GetCredentialException) {
        debug(
            "credential exception class=${e.javaClass.simpleName} type=${e.type} " +
                "message=${e.message.orEmpty().take(200)}"
        )
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) runCatching { Log.d(GOOGLE_SIGN_IN_TAG, message) }
    }
}

// Maps Credential Manager failures to messages that identify the failing layer
// (device account, Play services, Google Cloud OAuth config) without exposing tokens.
internal fun credentialManagerFailureMessage(e: GetCredentialException): String {
    val details = "${e.type} ${e.message.orEmpty()}".lowercase()
    return when {
        e is NoCredentialException || details.contains("no credential") ->
            "Google Credential Manager setup failed: no matching Google credential. " +
                "Check the Android OAuth client (package name + SHA-1) and the device Google account."
        e is GetCredentialProviderConfigurationException ->
            "Google Credential Manager setup failed: Google Play services credential provider " +
                "is unavailable or out of date on this device."
        e is GetCredentialInterruptedException ->
            "Google sign-in was interrupted. Please try again."
        details.contains("developer console") ||
            details.contains("developer error") ||
            details.contains("28444") ||
            details.contains("[10]") ||
            details.contains("10:") ->
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
            GoogleCredentialResult.Failure("Google ID token not returned.")
        }
    } catch (e: GoogleIdTokenParsingException) {
        debug(
            "credential exception class=${e.javaClass.simpleName} " +
                "message=${e.message.orEmpty().take(160)} idTokenReceived=false"
        )
        GoogleCredentialResult.Failure("Google ID token not returned.")
    } catch (e: IllegalArgumentException) {
        debug(
            "credential exception class=${e.javaClass.simpleName} " +
                "message=${e.message.orEmpty().take(160)} idTokenReceived=false"
        )
        GoogleCredentialResult.Failure("Google ID token not returned.")
    }
}

private val GOOGLE_ID_TOKEN_CREDENTIAL_TYPES = setOf(
    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
)
