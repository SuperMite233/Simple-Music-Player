package com.supermite.smp.logging

import org.junit.Assert.*
import org.junit.Test

class LogRedactorTest {
    @Test fun stripsHeadersJsonAndLegacyPasswordMessages() {
        val inputs=listOf(
            "Cookie: sessionid=fixture-cookie-value; csrftoken=fixture-csrf",
            "Authorization: Basic Zml4dHVyZTpzZWNyZXQ=",
            "{\"password\":\"fixture-password\",\"access_token\":\"fixture-token\",\"message\":\"safe\"}",
            "验证服务器: https://example.invalid, user=demo, pwd=fixture password with spaces, ignoreCert=false"
        )
        for (input in inputs) {
            val clean=LogRedactor.redact(input)
            for (secret in listOf("fixture-cookie-value","fixture-csrf","Zml4dHVyZTpzZWNyZXQ=","fixture-password","fixture-token","fixture password with spaces")) assertFalse(clean,clean.contains(secret))
            assertTrue(clean.contains("[REDACTED]"))
        }
    }

    @Test fun stripsEncodedUrlQueriesUserInfoAndContactAddresses() {
        val input="https://demo:fixture-password@example.invalid/image?%74oken=fixture-token&signature=fixture-sign demo@example.invalid /rest/view?u=demo&p=fixture-auth&t=fixture-hash&s=fixture-salt"
        val clean=LogRedactor.redact(input)
        for (secret in listOf("fixture-password","fixture-token","fixture-sign","demo@example.invalid","fixture-auth","fixture-hash","fixture-salt")) assertFalse(clean,clean.contains(secret))
    }

    @Test fun ordinaryOperationalMessagesRemainUseful() {
        val text="Subsonic getAlbum: HTTP 200; 42ms; tracks=12; cache hit"
        assertEquals(text,LogRedactor.redact(text))
    }

    @Test(timeout = 2000) fun longNonEmailPayloadDoesNotBlockDiagnostics() {
        val payload = "x".repeat(160_000)
        assertEquals("$payload contact [EMAIL]", LogRedactor.redact("$payload contact audit@example.invalid"))
    }
    @Test fun emailFollowedBySentencePunctuationIsRedacted() {
        assertEquals("contact [EMAIL].", LogRedactor.redact("contact audit@example.invalid."))
        assertEquals("contact [EMAIL]...", LogRedactor.redact("contact audit@example.invalid..."))
    }
    @Test(timeout = 2000) fun repeatedAtCharactersDoNotCauseUnboundedBacktracking() {
        val payload = "@".repeat(160_000)
        assertEquals(payload, LogRedactor.redact(payload))
    }
}
