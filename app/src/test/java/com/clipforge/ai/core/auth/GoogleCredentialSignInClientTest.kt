package com.clipforge.ai.core.auth

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleCredentialSignInClientTest {
    @Test
    fun googleIdTokenCredentialParsesToSuccess() {
        val credential = GoogleIdTokenCredential(
            "google-user",
            "header.payload.signature",
            null,
            null,
            null,
            null,
            null
        )

        val result = parseGoogleCredentialResult(credential, "raw-nonce")

        assertTrue(result is GoogleCredentialResult.Success)
        val success = result as GoogleCredentialResult.Success
        assertEquals("header.payload.signature", success.idToken)
        assertEquals("raw-nonce", success.nonce)
    }

    @Test
    fun signInWithGoogleCredentialTypeParsesToSuccess() {
        val googleCredential = GoogleIdTokenCredential(
            "google-user",
            "header.payload.signature",
            null,
            null,
            null,
            null,
            null
        )
        val credential = CustomCredential(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
            googleCredential.data
        )

        val result = parseGoogleCredentialResult(credential, "raw-nonce")

        assertTrue(result is GoogleCredentialResult.Success)
        val success = result as GoogleCredentialResult.Success
        assertEquals("header.payload.signature", success.idToken)
        assertEquals("raw-nonce", success.nonce)
    }

    @Test
    fun unsupportedCredentialMapsToCredentialManagerSetupFailure() {
        val credential = CustomCredential("unsupported", Bundle())

        val result = parseGoogleCredentialResult(credential, "raw-nonce")

        assertEquals(
            GoogleCredentialResult.Failure(
                "Google Credential Manager setup failed: unexpected credential type."
            ),
            result
        )
    }

    @Test
    fun noCredentialExceptionMapsToOAuthClientAndAccountGuidance() {
        val message = credentialManagerFailureMessage(
            NoCredentialException("No credentials available")
        )

        assertTrue(message.startsWith("Google Credential Manager setup failed: no matching Google credential."))
        assertTrue(message.contains("SHA-1"))
    }

    @Test
    fun providerConfigurationExceptionMapsToPlayServicesGuidance() {
        val message = credentialManagerFailureMessage(
            GetCredentialProviderConfigurationException("provider missing")
        )

        assertTrue(message.contains("Google Play services credential provider"))
    }

    @Test
    fun interruptedExceptionMapsToRetryMessage() {
        val message = credentialManagerFailureMessage(
            GetCredentialInterruptedException("interrupted")
        )

        assertEquals("Google sign-in was interrupted. Please try again.", message)
    }

    @Test
    fun developerConsoleErrorMapsToGoogleCloudProjectConfiguration() {
        val message = credentialManagerFailureMessage(
            GetCredentialCustomException(
                "androidx.credentials.TYPE_GET_CREDENTIAL_CUSTOM_EXCEPTION",
                "[28444] Developer console is not set up correctly."
            )
        )

        assertEquals(
            GOOGLE_CLOUD_PROJECT_CONFIG_MESSAGE,
            message
        )
        assertEquals(
            "28444",
            credentialStatusCode("[28444] Developer console is not set up correctly.")
        )
    }

    @Test
    fun unknownCredentialExceptionKeepsGenericMessageWithExceptionClass() {
        val message = credentialManagerFailureMessage(
            GetCredentialCustomException("custom-type", "something else")
        )

        assertEquals(
            "Google Credential Manager setup failed. (GetCredentialCustomException)",
            message
        )
    }

    @Test
    fun missingIdTokenMapsToIdTokenNotReturned() {
        val data = Bundle().apply {
            putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID", "google-user")
        }
        val credential = CustomCredential(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data
        )

        val result = parseGoogleCredentialResult(credential, "raw-nonce")

        assertEquals(
            GoogleCredentialResult.Failure(GOOGLE_ID_TOKEN_NOT_RETURNED_MESSAGE),
            result
        )
    }
}
