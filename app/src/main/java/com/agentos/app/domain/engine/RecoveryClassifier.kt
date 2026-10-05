package com.agentos.app.domain.engine

import com.agentos.app.domain.model.TaskStep
import com.agentos.app.service.AssistantAccessibilityService

/**
 * Classifies a step failure into a recovery strategy. The engine then decides
 * what to do: retry the same step, replan from current state, skip the step,
 * or fail the task.
 *
 * The classifier is intentionally rule-based and deterministic — it doesn't
 * call the AI; it inspects the exception type and message.
 */
object RecoveryClassifier {

    enum class Strategy {
        RETRY_STEP,        // same step, same args (transient)
        RETRY_AFTER_WAIT,  // same step, brief wait first (e.g. SPA still loading)
        REPLAN,            // ask the Main Agent for a new plan from current state
        SKIP_STEP,          // skip and continue (non-critical step)
        NEEDS_USER,         // surface to user, suspend until they respond
        FAIL_TASK           // unrecoverable — terminate
    }

    data class Decision(
        val strategy: Strategy,
        val reason: String,
        val waitMs: Long = 0L,
        val friendlyMessage: String = reason
    )

    /**
     * Classify a step failure. [attempt] is the number of times this step has
     * already been retried (0 = first failure, 1 = one retry already, ...).
     */
    fun classify(step: TaskStep, error: Throwable, attempt: Int): Decision {
        val msg = error.message.orEmpty()
        val tool = step.toolName.orEmpty()

        // Transient: no active window after launch — wait briefly and retry.
        if (error is AssistantAccessibilityService.NoActiveWindowException) {
            return if (attempt < 2) Decision(
                Strategy.RETRY_AFTER_WAIT,
                "no_active_window",
                waitMs = 800L,
                friendlyMessage = "Waiting for the target app to come to foreground…"
            ) else Decision(
                Strategy.REPLAN,
                "no_active_window_repeated",
                friendlyMessage = "The target app didn't come to foreground. Replanning with the current state…"
            )
        }

        // Transient: browser page still loading (SPA hydration race).
        if (tool.startsWith("browser_") && (
                    "no input matching" in msg ||
                    "no visible element containing text" in msg
                )) {
            return if (attempt < 1) Decision(
                Strategy.RETRY_AFTER_WAIT,
                "page_not_ready",
                waitMs = 1500L,
                friendlyMessage = "The page is still loading — retrying…"
            ) else Decision(
                Strategy.REPLAN,
                "page_unable_to_find_target",
                friendlyMessage = "Couldn't find the target on the page. Replanning with the current state…"
            )
        }

        // Network blip — retry.
        if (error is java.io.IOException || "timed out" in msg || "timeout" in msg) {
            return if (attempt < 1) Decision(
                Strategy.RETRY_AFTER_WAIT,
                "network_or_timeout",
                waitMs = 1000L,
                friendlyMessage = "A transient network/timeout error occurred — retrying…"
            ) else Decision(Strategy.REPLAN, "network_repeated", friendlyMessage = "Repeated network errors. Replanning…")
        }

        // Field rejected SET_TEXT — surface to user (some banking apps do this).
        if ("Field rejected SET_TEXT" in msg) {
            return Decision(Strategy.NEEDS_USER, "field_rejected_set_text",
                friendlyMessage = "The target app rejected accessibility typing. Open the field manually and the agent will retry.")
        }

        // Approval denied — abort (don't replan; user explicitly stopped).
        if (error is TaskEngine.ApprovalDeniedException) {
            return Decision(Strategy.FAIL_TASK, "approval_denied",
                friendlyMessage = "You denied approval. Task stopped.")
        }

        // Unknown tool — replan (the LLM hallucinated a tool name).
        if ("Tool '" in msg && "is not registered" in msg) {
            return Decision(Strategy.REPLAN, "unknown_tool",
                friendlyMessage = "The plan referenced an unknown tool. Asking the planner for a corrected plan…")
        }

        // Element not found on screen — replan (UI state has changed).
        if ("No UI element containing" in msg || "No editable field found" in msg) {
            return if (attempt < 1) Decision(
                Strategy.REPLAN,
                "element_not_found",
                friendlyMessage = "Couldn't find the target on screen. Replanning with the current state…"
            ) else Decision(Strategy.FAIL_TASK, "element_not_found_repeated",
                friendlyMessage = "Repeatedly couldn't find the target on screen.")
        }

        // Default: retry once, then replan.
        return if (attempt < 1) Decision(
            Strategy.RETRY_STEP, "transient_default", waitMs = 200L,
            friendlyMessage = "Step failed transiently — retrying…"
        ) else Decision(Strategy.REPLAN, "default_replan",
            friendlyMessage = "Step failed. Replanning with the current state…")
    }
}
