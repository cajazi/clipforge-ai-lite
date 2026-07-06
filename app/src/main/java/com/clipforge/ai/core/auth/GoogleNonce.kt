package com.clipforge.ai.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal data class GoogleNoncePair(
    val raw: String,
    val sha256Hex: String
)

internal object GoogleNonce {
    private val secureRandom = SecureRandom()

    fun create(byteLength: Int = 32): GoogleNoncePair {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        val raw = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
        return GoogleNoncePair(raw = raw, sha256Hex = sha256Hex(raw))
    }

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        val chars = CharArray(digest.size * 2)
        var index = 0
        for (byte in digest) {
            val unsigned = byte.toInt() and 0xff
            chars[index++] = HEX[unsigned ushr 4]
            chars[index++] = HEX[unsigned and 0x0f]
        }
        return String(chars)
    }

    private const val HEX = "0123456789abcdef"
}
