package com.clipforge.ai.core.auth

import android.os.Bundle
import androidx.credentials.CustomCredential
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
            GoogleCredentialResult.Failure("Google Credential Manager setup failed."),
            result
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
            GoogleCredentialResult.Failure("Google ID token not returned."),
            result
        )
    }
}
