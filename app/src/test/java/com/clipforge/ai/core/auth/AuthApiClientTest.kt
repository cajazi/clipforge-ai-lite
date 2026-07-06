package com.clipforge.ai.core.auth

import com.clipforge.ai.core.supabase.SUPABASE_MALFORMED_KEY_MESSAGE
import com.clipforge.ai.core.supabase.SUPABASE_MALFORMED_URL_MESSAGE
import com.clipforge.ai.core.supabase.SUPABASE_MISSING_CONFIG_MESSAGE
import com.clipforge.ai.core.supabase.SupabaseConfigValidator
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AuthApiClientTest {
    @Test
    fun supabaseUrlNormalizationTrimsWhitespaceAndTrailingSlash() {
        val validation = SupabaseConfigValidator.validate(
            rawUrl = " https://project.supabase.co/ ",
            rawAnonKey = " anon-key "
        )

        assertTrue(validation.isValid)
        assertEquals("https://project.supabase.co", validation.normalizedUrl)
        assertEquals("anon-key", validation.anonKey)
        assertEquals("project.supabase.co", validation.host)
        assertEquals("https://project.supabase.co/auth/v1", validation.authBaseUrl)
        assertEquals("https://project.supabase.co/rest/v1/", validation.restBaseUrl)
    }

    @Test
    fun malformedAndBlankConfigReturnsSafeErrors() {
        assertEquals(
            SUPABASE_MISSING_CONFIG_MESSAGE,
            SupabaseConfigValidator.validate("", "anon-key").error
        )
        assertEquals(
            SUPABASE_MISSING_CONFIG_MESSAGE,
            SupabaseConfigValidator.validate("https://project.supabase.co", " ").error
        )
        assertEquals(
            SUPABASE_MALFORMED_URL_MESSAGE,
            SupabaseConfigValidator.validate("http://project.supabase.co", "anon-key").error
        )
        assertEquals(
            SUPABASE_MALFORMED_URL_MESSAGE,
            SupabaseConfigValidator.validate("https://project.supabase.co/rest/v1", "anon-key").error
        )
        assertEquals(
            SUPABASE_MALFORMED_KEY_MESSAGE,
            SupabaseConfigValidator.validate("https://project.supabase.co", "PASTE_ANON_KEY_HERE").error
        )
    }

    @Test
    fun googleWebClientIdValidationRequiresWebClientIdShape() {
        assertEquals(
            GOOGLE_WEB_CLIENT_ID_MISSING_MESSAGE,
            GoogleSignInConfig.validate(" ").error
        )
        assertEquals(
            GOOGLE_WEB_CLIENT_ID_MALFORMED_MESSAGE,
            GoogleSignInConfig.validate("android-client-id").error
        )

        val validation = GoogleSignInConfig.validate(
            " $EXPECTED_GOOGLE_WEB_CLIENT_ID "
        )
        assertTrue(validation.isValid)
        assertEquals(EXPECTED_GOOGLE_WEB_CLIENT_ID, validation.webClientId)
        assertTrue(validation.clientIdMatchesExpected)
    }

    @Test
    fun googleWebClientIdValidationRejectsEmbeddedWhitespaceMismatch() {
        val validation = GoogleSignInConfig.validate(
            "89347226051    9-l5b0acsci1qnr6orbem6cn00f0vaoofsg.apps.googleusercontent.com"
        )

        assertEquals(GOOGLE_WEB_CLIENT_ID_MALFORMED_MESSAGE, validation.error)
        assertFalse(validation.clientIdMatchesExpected)
        assertTrue(validation.clientIdMalformed)
    }

    @Test
    fun authErrorMappingUsesUsefulMessages() {
        val client = authClient()

        assertEquals(
            "Incorrect email or password.",
            client.parseResponse(
                """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}""",
                400
            ).error
        )
        assertEquals(
            "Please verify your email before signing in.",
            client.parseResponse("""{"msg":"Email not confirmed"}""", 400).error
        )
        assertEquals(
            "Auth config error. Check the Supabase anon key.",
            client.parseResponse("""{"message":"Invalid API key"}""", 401).error
        )
    }

    @Test
    fun loginSuccessJsonParsingExtractsSessionAndUser() {
        val result = authClient().parseResponse(
            """
            {
              "access_token": "access-token",
              "refresh_token": "refresh-token",
              "user": {
                "id": "user-1",
                "email": "person@example.com"
              }
            }
            """.trimIndent(),
            200
        )

        assertNull(result.error)
        assertEquals("user-1", result.userId)
        assertEquals("person@example.com", result.email)
        assertEquals("access-token", result.accessToken)
        assertEquals("refresh-token", result.refreshToken)
    }

    @Test
    fun signupSuccessJsonParsingExtractsSessionAndUser() {
        val result = authClient().parseResponse(
            """
            {
              "access_token": "signup-access",
              "refresh_token": "signup-refresh",
              "user": {
                "id": "new-user",
                "email": "new@example.com"
              }
            }
            """.trimIndent(),
            200
        )

        assertNull(result.error)
        assertFalse(result.needsConfirmation)
        assertEquals("new-user", result.userId)
        assertEquals("new@example.com", result.email)
        assertEquals("signup-access", result.accessToken)
        assertEquals("signup-refresh", result.refreshToken)
    }

    @Test
    fun signupEmailConfirmationFlowDoesNotFailRegistration() {
        val result = authClient().parseResponse(
            """
            {
              "user": {
                "id": "pending-user",
                "email": "pending@example.com"
              }
            }
            """.trimIndent(),
            200
        )

        assertNull(result.error)
        assertTrue(result.needsConfirmation)
        assertEquals("pending-user", result.userId)
        assertEquals("pending@example.com", result.email)
        assertNull(result.accessToken)
    }

    @Test
    fun non2xxSupabaseResponsesAreMappedWithoutNetworkException() {
        val client = authClient()

        assertEquals(
            "Auth config error. Check the Supabase anon key.",
            client.parseResponse("", 401).error
        )
        assertEquals(
            "Supabase auth endpoint not found. Check SUPABASE_URL.",
            client.parseResponse("", 404).error
        )
        assertEquals(
            "Supabase auth service error. Please try again.",
            client.parseResponse("", 503).error
        )
    }

    @Test
    fun signInRequestUsesSupabasePasswordEndpointHeadersAndEscapedJson() = runBlocking {
        val fake = FakeCallFactory(
            code = 200,
            body = """{"access_token":"access","refresh_token":"refresh","user":{"id":"u1","email":"person@example.com"}}"""
        )
        val client = authClient(fake)

        val result = client.signIn(" person@example.com ", "p\"\\word")

        assertNull(result.error)
        val request = requireNotNull(fake.request)
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/token", request.url.encodedPath)
        assertEquals("grant_type=password", request.url.encodedQuery)
        assertEquals(TEST_ANON_KEY, request.header("apikey"))
        assertEquals("Bearer $TEST_ANON_KEY", request.header("Authorization"))
        assertEquals("application/json", request.header("Content-Type"))

        val sent = request.bodyAsString()
        val json = Gson().fromJson(sent, JsonObject::class.java)
        assertEquals("person@example.com", json.get("email").asString)
        assertEquals("p\"\\word", json.get("password").asString)
    }

    @Test
    fun signUpRequestUsesSupabaseSignupEndpointHeadersAndEscapedJson() = runBlocking {
        val fake = FakeCallFactory(
            code = 200,
            body = """{"user":{"id":"pending","email":"new@example.com"}}"""
        )
        val client = authClient(fake)

        val result = client.signUp("new@example.com", "pass\"\\word", "Ada Lovelace")

        assertNull(result.error)
        assertTrue(result.needsConfirmation)
        val request = requireNotNull(fake.request)
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/signup", request.url.encodedPath)
        assertEquals(TEST_ANON_KEY, request.header("apikey"))
        assertEquals("Bearer $TEST_ANON_KEY", request.header("Authorization"))

        val json = Gson().fromJson(request.bodyAsString(), JsonObject::class.java)
        assertEquals("new@example.com", json.get("email").asString)
        assertEquals("pass\"\\word", json.get("password").asString)
        assertEquals("Ada Lovelace", json.getAsJsonObject("data").get("display_name").asString)
    }

    @Test
    fun googleIdTokenRequestUsesSupabaseIdTokenEndpointHeadersAndNonce() = runBlocking {
        val fake = FakeCallFactory(
            code = 200,
            body = """{"access_token":"access","refresh_token":"refresh","user":{"id":"u1","email":"person@example.com"}}"""
        )
        val client = authClient(fake)

        val result = client.signInWithGoogleIdToken(fakeGoogleJwt(aud = TEST_WEB_CLIENT_ID), "nonce-value")

        assertNull(result.error)
        val request = requireNotNull(fake.request)
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/token", request.url.encodedPath)
        assertEquals("grant_type=id_token", request.url.encodedQuery)
        assertEquals(TEST_ANON_KEY, request.header("apikey"))
        assertEquals("Bearer $TEST_ANON_KEY", request.header("Authorization"))
        assertEquals("application/json", request.header("Content-Type"))

        val json = Gson().fromJson(request.bodyAsString(), JsonObject::class.java)
        assertEquals("google", json.get("provider").asString)
        assertEquals(fakeGoogleJwt(aud = TEST_WEB_CLIENT_ID), json.get("id_token").asString)
        assertEquals("nonce-value", json.get("nonce").asString)
    }

    @Test
    fun googleIdTokenRequestOmitsBlankNonce() = runBlocking {
        val fake = FakeCallFactory(
            code = 200,
            body = """{"access_token":"access","refresh_token":"refresh","user":{"id":"u1","email":"person@example.com"}}"""
        )
        val client = authClient(fake)

        val result = client.signInWithGoogleIdToken(fakeGoogleJwt(aud = TEST_WEB_CLIENT_ID), " ")

        assertNull(result.error)
        val json = Gson().fromJson(requireNotNull(fake.request).bodyAsString(), JsonObject::class.java)
        assertFalse(json.has("nonce"))
    }

    @Test
    fun missingGoogleIdTokenIsMappedBeforeNetwork() = runBlocking {
        val fake = FakeCallFactory()
        val client = authClient(fake)

        val result = client.signInWithGoogleIdToken("", "nonce-value")

        assertEquals(GOOGLE_ID_TOKEN_NOT_RETURNED_MESSAGE, result.error)
        assertNull(fake.request)
    }

    @Test
    fun googleIdTokenAudienceMismatchIsMappedBeforeNetwork() = runBlocking {
        val fake = FakeCallFactory()
        val client = authClient(fake)

        val result = client.signInWithGoogleIdToken(
            fakeGoogleJwt(aud = "android-client-id.apps.googleusercontent.com"),
            "nonce-value"
        )

        assertEquals("OAuth client ID mismatch. Check Google Web Client ID setup.", result.error)
        assertNull(fake.request)
    }

    @Test
    fun googleIdTokenAudienceMatchWithAzpAllowsExchange() = runBlocking {
        val fake = FakeCallFactory(
            code = 200,
            body = """{"access_token":"access","refresh_token":"refresh","user":{"id":"u1","email":"person@example.com"}}"""
        )
        val client = authClient(fake)

        val result = client.signInWithGoogleIdToken(
            fakeGoogleJwt(aud = TEST_WEB_CLIENT_ID, azp = "android-client-id.apps.googleusercontent.com"),
            "nonce-value"
        )

        assertNull(result.error)
        assertNotNull(fake.request)
    }

    @Test
    fun supabaseGoogleIdTokenNonceRejectionIsMappedSafely() = runBlocking {
        val fake = FakeCallFactory(
            code = 400,
            body = """{"error":"bad_jwt","msg":"nonce mismatch"}"""
        )
        val client = authClient(fake)

        val result = client.signInWithGoogleIdToken(fakeGoogleJwt(aud = TEST_WEB_CLIENT_ID), "nonce-value")

        assertEquals("Supabase rejected Google token: nonce verification failed.", result.error)
    }

    @Test
    fun supabaseGoogleIdTokenAudienceRejectionIsMappedSafely() = runBlocking {
        val client = authClient()

        assertEquals(
            "OAuth client ID mismatch. Check Google Web Client ID setup.",
            client.parseGoogleIdTokenResponse(
                """{"error":"bad_jwt","msg":"invalid audience in id token"}""",
                400
            ).error
        )
    }

    @Test
    fun deepLinkRedirectUrlMatchesManifestContract() {
        assertEquals("clipforgeai://auth-callback", OAUTH_REDIRECT)
    }

    private fun authClient(callFactory: Call.Factory = FakeCallFactory()): AuthApiClient =
        AuthApiClient(
            rawSupabaseUrl = TEST_URL,
            rawAnonKey = TEST_ANON_KEY,
            expectedGoogleWebClientId = TEST_WEB_CLIENT_ID,
            callFactory = callFactory
        )

    private fun Request.bodyAsString(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }

    private class FakeCallFactory(
        private val code: Int = 200,
        private val body: String = """{"user":{"id":"u","email":"u@example.com"}}"""
    ) : Call.Factory {
        var request: Request? = null

        override fun newCall(request: Request): Call {
            this.request = request
            return FakeCall(request, code, body)
        }
    }

    private class FakeCall(
        private val request: Request,
        private val code: Int,
        private val body: String
    ) : Call {
        override fun request(): Request = request

        override fun execute(): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()

        override fun enqueue(responseCallback: Callback) {
            throw UnsupportedOperationException("Synchronous fake only")
        }

        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = FakeCall(request, code, body)
    }

    private fun fakeGoogleJwt(aud: String, azp: String? = null): String {
        val payload = mutableMapOf("aud" to aud)
        azp?.let { payload["azp"] = it }
        return listOf(
            base64Url("""{"alg":"none"}"""),
            base64Url(Gson().toJson(payload)),
            "signature"
        ).joinToString(".")
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val TEST_URL = "https://project.supabase.co"
        const val TEST_ANON_KEY = "test-anon-key"
        const val TEST_WEB_CLIENT_ID = "1234567890-web.apps.googleusercontent.com"
    }
}
