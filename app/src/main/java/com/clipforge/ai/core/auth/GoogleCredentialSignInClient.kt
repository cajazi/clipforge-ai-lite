package com.clipforge.ai.core.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.clipforge.ai.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom

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

        val nonce = createNonce()
        val signInOption = GetSignInWithGoogleOption.Builder(config.webClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        debug("native prompt requested clientIdConfigured=true")
        return try {
            val response = credentialManager.getCredential(
                context = context,
                request = request
            )
            val credential = response.credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleCredential.idToken
                if (idToken.isBlank()) {
                    GoogleCredentialResult.Failure("Google sign-in did not return an ID token.")
                } else {
                    debug("native prompt completed idTokenPresent=true")
                    GoogleCredentialResult.Success(idToken = idToken, nonce = nonce)
                }
            } else {
                debug("native prompt returned unexpectedCredential=${credential::class.java.simpleName}")
                GoogleCredentialResult.Failure("Google sign-in returned an unsupported credential.")
            }
        } catch (e: GetCredentialCancellationException) {
            debug("native prompt cancelled")
            GoogleCredentialResult.Cancelled
        } catch (e: GoogleIdTokenParsingException) {
            debug("id token parsing failed: ${e.javaClass.simpleName}")
            GoogleCredentialResult.Failure("Google sign-in returned an invalid ID token.")
        } catch (e: GetCredentialException) {
            debug("native prompt failed: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(160)}")
            GoogleCredentialResult.Failure("Google sign-in failed. Check the OAuth client setup.")
        } catch (e: IllegalArgumentException) {
            debug("native prompt config rejected: ${e.message.orEmpty().take(160)}")
            GoogleCredentialResult.Failure(GOOGLE_WEB_CLIENT_ID_MALFORMED_MESSAGE)
        }
    }

    private fun createNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) runCatching { Log.d(GOOGLE_SIGN_IN_TAG, message) }
    }
}
