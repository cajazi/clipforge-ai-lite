package com.clipforge.ai.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNonceTest {
    @Test
    fun sha256HexUsesSupabaseExpectedNonceHashFormat() {
        assertEquals(
            "efb4e26c3deb3dd5e04408769d1b6b371ae1e7acbe1e32332550b06f784780f2",
            GoogleNonce.sha256Hex("nonce-value")
        )
    }

    @Test
    fun createReturnsRawNonceAndDifferentSha256HexNonce() {
        val nonce = GoogleNonce.create()

        assertTrue(nonce.raw.isNotBlank())
        assertEquals(64, nonce.sha256Hex.length)
        assertFalse(nonce.raw == nonce.sha256Hex)
        assertEquals(GoogleNonce.sha256Hex(nonce.raw), nonce.sha256Hex)
    }
}
