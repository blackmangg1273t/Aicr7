package com.agentos.app

import com.agentos.app.domain.engine.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {

    private val parser = PlanParser(setOf("main", "research", "browser", "android", "terminal", "coding"))

    @Test
    fun parsesCleanJson() {
        val raw = """
            {"direct_answer": false, "answer": null, "steps": [
              {"agent": "research", "tool": "web_search", "args": {"query": "best budget gaming phone"}, "why": "find options"},
              {"agent": "main", "tool": null, "args": {}, "why": "synthesize"}
            ]}
        """.trimIndent()
        val plan = parser.parse(raw)
        assertEquals(2, plan.steps.size)
        assertEquals("research", plan.steps[0].agent)
        assertEquals("web_search", plan.steps[0].tool)
        assertEquals("synthesize", plan.steps[1].why)
        assertTrue(!plan.directAnswer)
    }

    @Test
    fun parsesFencedJsonWithProse() {
        val raw = """
            Here is my plan:
            ```json
            {"direct_answer": true, "answer": "Hello!", "steps": []}
            ```
            Hope that helps.
        """.trimIndent()
        val plan = parser.parse(raw)
        assertTrue(plan.directAnswer)
        assertEquals("Hello!", plan.answerText)
    }

    @Test
    fun dropsUnknownAgents() {
        val raw = """
            {"direct_answer": false, "steps": [
              {"agent": "research", "tool": "web_search", "args": {"query": "x"}, "why": ""},
              {"agent": "hacker_agent", "tool": "shell_exec", "args": {"command": "rm -rf /"}, "why": ""}
            ]}
        """.trimIndent()
        val plan = parser.parse(raw)
        assertEquals(1, plan.steps.size)
        assertEquals("research", plan.steps[0].agent)
    }

    @Test
    fun capsStepsAtEight() {
        val step = """{"agent": "main", "tool": null, "args": {}, "why": "n"}"""
        val raw = """{"direct_answer": false, "steps": [${(1..12).joinToString(",") { step }}]}"""
        val plan = parser.parse(raw)
        assertEquals(8, plan.steps.size)
    }

    @Test(expected = PlanParser.PlanParseException::class)
    fun throwsWhenNoJsonAtAll() {
        parser.parse("I cannot answer that question in JSON, sorry.")
    }

    @Test
    fun extractsFirstBalancedObject() {
        val text = """prefix {"a": {"b": 1}, "c": "x}y"} suffix"""
        val extracted = PlanParser.extractFirstJsonObject(text)
        assertEquals("""{"a": {"b": 1}, "c": "x}y"}""", extracted)
    }

    @Test
    fun extractReturnsNullWithoutBrace() {
        assertNull(PlanParser.extractFirstJsonObject("no braces here"))
    }

    @Test
    fun directAnswerWithStepsIsNotDirect() {
        val raw = """{"direct_answer": true, "answer": "x", "steps": [{"agent": "research", "tool": "web_search", "args": {"query":"q"}, "why": ""}]}"""
        val plan = parser.parse(raw)
        // If the planner still requested steps, they win over the direct-answer flag
        assertTrue(!plan.directAnswer)
    }
}
