package com.agentos.app.domain.engine

import com.agentos.app.core.logging.Logger
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
 *
 * Additionally:
 *  - Validates agent names against [knownAgents]; drops unknown agents with
 *    a structured [Drop] record (so the engine can surface them as events).
 *  - Validates tool names against [knownTools] when provided; unknown tool
 *    names are reported via [Drop] (the step is kept, but the engine will
 *    emit a recovery event before execution).
 *  - Rejects empty non-direct plans via [PlanParseException].
 */
class PlanParser(
    private val knownAgents: Set<String>,
    private val knownTools: Set<String> = emptySet()
) {

    class PlanParseException(message: String) : Exception(message)

    data class Drop(val agent: String?, val tool: String?, val reason: String)

    /** The parsed plan plus any dropped steps (for visibility in the UI). */
    data class ParsedPlan(val plan: AgentPlan, val dropped: List<Drop>)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Convenience for callers that just want the plan (no drop info). */
    fun parse(raw: String): AgentPlan = parseWithDrops(raw).plan

    fun parseWithDrops(raw: String): ParsedPlan {
        val cleaned = stripFences(raw)
        val candidate = extractFirstJsonObject(cleaned)
            ?: throw PlanParseException("Planner returned no JSON object. Raw: ${raw.take(200)}")

        return try {
            val obj = json.parseToJsonElement(candidate).jsonObject
            val directAnswer = obj["direct_answer"]?.let { it.jsonPrimitive.booleanOrNull } ?: false
            val answer = obj["answer"]?.jsonPrimitive?.content
            val stepsJson = obj["steps"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            val drops = mutableListOf<Drop>()
            val steps = stepsJson.mapNotNull { el ->
                (el as? JsonObject)?.let { parseStep(it, drops) }
            }.take(8)

            if (!directAnswer && steps.isEmpty()) {
                throw PlanParseException(
                    "Plan has no executable steps and direct_answer is false. " +
                    "Either mark direct_answer=true with an answer, or provide at least one valid step."
                )
            }

            ParsedPlan(
                AgentPlan(
                    directAnswer = directAnswer && steps.isEmpty(),
                    answerText = answer,
                    steps = steps
                ),
                drops
            )
        } catch (e: PlanParseException) {
            throw e
        } catch (e: Exception) {
            throw PlanParseException("Failed to parse plan JSON: ${e.message}")
        }
    }

    private fun parseStep(obj: JsonObject, drops: MutableList<Drop>): PlanStepRequest? {
        val agent = obj["agent"]?.jsonPrimitive?.content ?: run {
            drops += Drop(null, null, "step missing 'agent' field")
            return null
        }
        val agentKey = agent.trim().lowercase()
        if (!knownAgents.contains(agentKey)) {
            Logger.w("PlanParser", "Dropping step for unknown agent '$agent'")
            drops += Drop(agent, null, "unknown agent '$agent' (known: ${knownAgents.joinToString(",")})")
            return null
        }
        val tool = obj["tool"]?.let { t ->
            val s = t.jsonPrimitive.content
            s.ifBlank { null }
        }
        // Validate tool name (if registry was provided). Keep the step — the
        // engine may still want to surface it for replanning — but record the drop.
        if (tool != null && knownTools.isNotEmpty() && !knownTools.contains(tool)) {
            Logger.w("PlanParser", "Plan references unknown tool '$tool'")
            drops += Drop(agent, tool, "unknown tool '$tool' (known: ${knownTools.take(20).joinToString(",")}…)")
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
