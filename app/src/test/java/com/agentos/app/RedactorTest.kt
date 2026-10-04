package com.agentos.app

import com.agentos.app.core.logging.Logger
import com.agentos.app.core.logging.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logs must never contain raw secrets (SECURITY.md guarantee). */
class RedactorTest {

    @Test
    fun redactsOpenAiKeys() {
        val out = Redactor.redact("Calling with key sk-proj-abcd1234EFGH5678ijkl done")
        assertFalse(out.contains("sk-proj-abcd1234EFGH5678ijkl"))
        assertTrue(out.contains("***"))
    }

    @Test
    fun redactsGoogleKeys() {
        val out = Redactor.redact("url: https://api.google.com/v1beta/models?key=AIzaSyD-abc123def456ghi789jkl_mno")
        assertFalse(out.contains("AIzaSyD-abc123def456ghi789jkl_mno"))
    }

    @Test
    fun redactsGithubTokensAndBearer() {
        val out = Redactor.redact("Authorization: Bearer ghp_16CharsAbcdefgh1234 and github_pat_TESTTOKEN0000000000000000000")
        assertFalse(out.contains("ghp_16CharsAbcdefgh1234"))
        assertFalse(out.contains("github_pat_TESTTOKEN0000000000000000000"))
    }

    @Test
    fun redactsKeyEqualsValue() {
        val out = Redactor.redact("apikey=super-secret-value")
        assertFalse(out.contains("super-secret-value"))
    }

    @Test
    fun keepsNormalText() {
        val text = "Task completed in 1200ms with 5 results"
        assertEquals(text, Redactor.redact(text))
    }

    @Test
    fun loggerEntriesAreRedacted() {
        Logger.e("Test", "leaking sk-1234567890abcdefghij here")
        val last = Logger.recent().last()
        assertFalse(last.message.contains("sk-1234567890abcdefghij"))
        Logger.clear()
    }
}
