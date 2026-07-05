package com.clipforge.ai.core.auth

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Base64

internal data class GoogleIdTokenAudience(
    val aud: List<String>,
    val azp: String?,
    val expectedAudience: String
) {
    val audMatchesExpected: Boolean =
        expectedAudience.isNotBlank() && aud.any { it == expectedAudience }

    val audForLog: String =
        aud.takeIf { it.isNotEmpty() }?.joinToString(separator = ",") ?: "none"
}

internal object GoogleIdTokenDiagnostics {
    private val gson = Gson()

    fun inspect(idToken: String, expectedAudience: String): GoogleIdTokenAudience? {
        val parts = idToken.split(".")
        if (parts.size < 2) return null
        val payload = decodeJwtPart(parts[1]) ?: return null
        val obj = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
            ?: return null
        val aud = obj.audienceValues()
        if (aud.isEmpty()) return null
        return GoogleIdTokenAudience(
            aud = aud,
            azp = obj.stringOrNull("azp"),
            expectedAudience = expectedAudience.trim()
        )
    }

    private fun decodeJwtPart(part: String): String? =
        runCatching {
            val padded = part.padEnd(part.length + ((4 - part.length % 4) % 4), '=')
            String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        }.getOrNull()

    private fun JsonObject.audienceValues(): List<String> {
        val element = get("aud") ?: return emptyList()
        return when {
            element.isJsonPrimitive -> listOfNotNull(
                runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
            )
            element.isJsonArray -> element.asJsonArray.stringValues()
            else -> emptyList()
        }
    }

    private fun JsonArray.stringValues(): List<String> =
        mapNotNull { element ->
            if (!element.isJsonPrimitive) {
                null
            } else {
                runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }

    private fun JsonObject.stringOrNull(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
