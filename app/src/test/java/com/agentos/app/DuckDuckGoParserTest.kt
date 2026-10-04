package com.agentos.app

import com.agentos.app.data.tools.WebSearchTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the DuckDuckGo HTML result parser (real parsing logic
 * used by WebSearchTool).
 */
class DuckDuckGoParserTest {

    @Test
    fun parsesStandardResults() {
        val html = """
            <html><body>
            <div class="result results_links">
              <h2 class="result__title"><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fphone1&amp;rut=abc">Budget Gaming Phone 1</a></h2>
              <a class="result__snippet" href="#">Great gaming phone under budget with strong battery.</a>
            </div>
            <div class="result results_links">
              <h2 class="result__title"><a rel="nofollow" class="result__a" href="https://direct.example.org/phone2">Budget Gaming Phone 2</a></h2>
              <a class="result__snippet" href="#">Another solid option for mobile gaming.</a>
            </div>
            </body></html>
        """.trimIndent()

        val results = WebSearchTool.parseDuckDuckGo(html, 5)
        assertEquals(2, results.size)
        assertEquals("https://example.com/phone1", results[0].url)
        assertEquals("Budget Gaming Phone 1", results[0].title)
        assertTrue(results[0].snippet.contains("gaming"))
        assertEquals("https://direct.example.org/phone2", results[1].url)
    }

    @Test
    fun respectsMaxResults() {
        val one = """
            <h2 class="result__title"><a class="result__a" href="https://a.com">A</a></h2>
            <a class="result__snippet">s1</a>
        """.trimIndent()
        val html = one + one + one
        val results = WebSearchTool.parseDuckDuckGo(html, 2)
        assertEquals(2, results.size)
    }

    @Test
    fun emptyHtmlYieldsNoResults() {
        assertTrue(WebSearchTool.parseDuckDuckGo("<html></html>", 5).isEmpty())
    }
}
