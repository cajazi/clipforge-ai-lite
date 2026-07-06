package com.clipforge.ai.core.supabase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class SupabaseClientTest {

    @Test
    fun supabaseClientObjectLoadsWithoutCreatingRetrofit() {
        assertNotNull(SupabaseClient)
    }

    @Test
    fun blankConfigCreatesUnavailableApiWithoutRetrofitCrash() = runBlocking {
        val validation = SupabaseConfigValidator.validate("", "")
        val api = SupabaseClient.create(SupabaseProjectApi::class.java, validation)

        assertUnavailable(SUPABASE_MISSING_CONFIG_MESSAGE) {
            api.createProject(projectBody())
        }
    }

    @Test
    fun invalidUrlCreatesUnavailableApiWithoutRetrofitCrash() = runBlocking {
        val validation = SupabaseConfigValidator.validate("project.supabase.co", "anon-key")
        val api = SupabaseClient.create(SupabaseProjectApi::class.java, validation)

        assertUnavailable(SUPABASE_MALFORMED_URL_MESSAGE) {
            api.createProject(projectBody())
        }
    }

    @Test
    fun validUrlNormalizesRestBaseUrl() {
        val validation = SupabaseConfigValidator.validate(
            rawUrl = " https://project.supabase.co/ ",
            rawAnonKey = " anon-key "
        )

        assertEquals(
            "https://project.supabase.co/rest/v1/",
            SupabaseClient.buildRestBaseUrl(validation)
        )
    }

    private suspend fun assertUnavailable(
        expectedMessage: String,
        block: suspend () -> Unit
    ) {
        try {
            block()
            fail("Expected SupabaseUnavailableException")
        } catch (e: SupabaseUnavailableException) {
            assertEquals(expectedMessage, e.message)
        }
    }

    private fun projectBody() = CreateProjectBody(
        id = "project-id",
        title = "Project",
        aspectRatio = "RATIO_9_16",
        exportQuality = "QUALITY_720P"
    )
}
