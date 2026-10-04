package com.agentos.app.domain.engine

import com.agentos.app.domain.model.AgentPlan
import com.agentos.app.domain.model.PlanStepRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Robust parser for the AI planner's JSON plan. Handles markdown fences,
 * leading prose, and schema drift. Throws [PlanParseException] with an
 * actionable message when nothing usable can be extracted.
 */
class PlanParser(private val knownAgents: Set<String>) {

    class PlanParseException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): AgentPlan {
        val cleaned = stripFences(raw)
        val candidate = extractFirstJsonObject(cleaned)
            ?: throw PlanParseException("Planner returned no JSON object. Raw: ${raw.take(200)}")

        return try {
            val obj = json.parseToJsonElement(candidate).jsonObject
            val directAnswer = obj["direct_answer"]?.let { it.jsonPrimitive.booleanOrNull } ?: false
            val answer = obj["answer"]?.jsonPrimitive?.content
            val stepsJson = obj["steps"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            val steps = stepsJson.mapNotNull { el ->
                (el as? JsonObject)?.let { parseStep(it) }
            }.filter { step ->
                val known = knownAgents.contains(step.agent.trim().lowercase())
                if (!known) println("PlanParser: dropping step for unknown agent '${step.agent}'")
                known
            }.take(8)

            AgentPlan(
                directAnswer = directAnswer && steps.isEmpty(),
                answerText = answer,
                steps = steps
            )
        } catch (e: PlanParseException) {
            throw e
        } catch (e: Exception) {
            throw PlanParseException("Failed to parse plan JSON: ${e.message}")
        }
    }

    private fun parseStep(obj: JsonObject): PlanStepRequest? {
        val agent = obj["agent"]?.jsonPrimitive?.content ?: return null
        val tool = obj["tool"]?.let { t ->
            val s = t.jsonPrimitive.content
            s.ifBlank { null }
        }
        val args = (obj["args"] as? JsonObject) ?: JsonObject(emptyMap())
        val why = obj["why"]?.jsonPrimitive?.content ?: ""
        val retry = obj["retry_on_failure"]?.jsonPrimitive?.booleanOrNull ?: false
        return PlanStepRequest(agent = agent, tool = tool, args = args, why = why, retryOnFailure = retry)
    }

    companion object {
        fun stripFences(raw: String): String = raw
            .replace(Regex("```json", RegexOption.IGNORE_CASE), "```")
            .split("```")
            .firstOrNull { it.contains('{') }
            ?: raw

        /** Extracts the first balanced {...} block from arbitrary text. */
        fun extractFirstJsonObject(text: String): String? {
            val start = text.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until text.length) {
                val c = text[i]
                if (escaped) { escaped = false; continue }
                when {
                    c == '\\' && inString -> escaped = true
                    c == '"' -> inString = !inString
                    !inString && c == '{' -> depth++
                    !inString && c == '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            return null
        }
    }
}
