package com.agentos.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real Android UI automation service. Enabled ONLY when the user explicitly
 * turns it on in system Settings > Accessibility. Provides:
 * - tap / long-press / swipe via real gesture dispatch
 * - click nodes by visible text or resource id
 * - type text into the focused editable field
 * - scroll, global back/home actions
 * - a structured snapshot of the current screen for the agent
 */
class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "A11yService"
        @Volatile
        private var instance: AssistantAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
        fun require(): AssistantAccessibilityService =
            instance ?: throw AccessibilityNotEnabled(
                "Accessibility service is not enabled. Enable 'AgentOS Android Agent' in Settings > Accessibility."
            )
    }

    class AccessibilityNotEnabled(message: String) : Exception(message)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Logger.i(TAG, "Accessibility service connected — Android automation available")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Logger.w(TAG, "Accessibility service unbound — Android automation disabled")
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    /* ---------------------- gestures ---------------------- */

    suspend fun tap(x: Int, y: Int): Boolean = dispatchSingle(x, y, 50L)

    suspend fun longPress(x: Int, y: Int): Boolean = dispatchSingle(x, y, 600L)

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L): Boolean {
        val service = require()
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<Boolean>()
            val ok = service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { deferred.complete(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { deferred.complete(false) }
                }, null)
            if (ok) deferred.await() else false
        }
    }

    private suspend fun dispatchSingle(x: Int, y: Int, durationMs: Long): Boolean {
        val service = require()
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<Boolean>()
            val ok = service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { deferred.complete(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { deferred.complete(false) }
                }, null)
            if (ok) deferred.await() else false
        }
    }

    /* ---------------------- node actions ---------------------- */

    suspend fun clickByText(text: String): String {
        val service = require()
        return withTimeoutOrNull(5_000) {
            withContext(Dispatchers.Main) {
                val root = service.rootInActiveWindow ?: throw AccessibilityNotEnabled("No active window is accessible right now")
                val nodes = root.findAccessibilityNodeInfosByText(text)
                val match = nodes.firstOrNull { n ->
                    n.isClickable || (n.text?.toString()?.contains(text, ignoreCase = true) == true)
                } ?: nodes.firstOrNull() ?: throw NoSuchElementException("No UI element containing \"$text\" found on screen")
                val bounds = Rect().also { match.getBoundsInScreen(it) }
                val clickable = walkToClickable(match) ?: match
                val performed = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!performed) {
                    // fall back to a real gesture on the node's center
                    val c = Rect().also { clickable.getBoundsInScreen(it) }
                    val cx = c.centerX(); val cy = c.centerY()
                    withContext(Dispatchers.Main) { tap(cx, cy) }
                    "Clicked \"$text\" via gesture at ($cx, $cy)"
                } else {
                    "Clicked \"$text\" (bounds: ${bounds.width()}x${bounds.height()} at ${bounds.left},${bounds.top})"
                }
            }
        } ?: throw IllegalStateException("clickByText timed out")
    }

    private fun walkToClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && !cur.isClickable && hops < 6) {
            cur = cur.parent
            hops++
        }
        return cur?.takeIf { it.isClickable }
    }

    suspend fun typeText(text: String, intoHint: String? = null): String {
        val service = require()
        return withTimeoutOrNull(5_000) {
            withContext(Dispatchers.Main) {
                val root = service.rootInActiveWindow ?: throw AccessibilityNotEnabled("No active window is accessible right now")
                val target = findEditable(root, intoHint)
                    ?: throw NoSuchElementException(
                        "No editable field found" + (intoHint?.let { " matching \"$it\"" } ?: ". Tap a field first, then retry.")
                    )
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!ok) {
                    // fallback: focus + real clipboard-free typing via ACTION_PASTE is unreliable; report honestly
                    throw IllegalStateException("Field rejected SET_TEXT action (app $packageName may restrict accessibility editing)")
                }
                "Typed ${text.length} chars into ${target.viewIdResourceName ?: target.className}"
            }
        } ?: throw IllegalStateException("typeText timed out")
    }

    private fun findEditable(root: AccessibilityNodeInfo, hint: String?): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().also { it.add(root) }
        var visited = 0
        var fallbackEditable: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && visited < 400) {
            val n = queue.removeFirst()
            visited++
            if (n.isEditable || (n.className?.toString()?.contains("EditText") == true)) {
                val matchHint = hint?.let { h ->
                    val hay = listOfNotNull(n.text?.toString(), n.contentDescription?.toString(), n.hintText?.toString(), n.viewIdResourceName).joinToString(" ")
                    hay.contains(h, ignoreCase = true)
                } ?: false
                if (matchHint || hint == null) {
                    if (hint != null) return n
                    if (fallbackEditable == null) fallbackEditable = n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return fallbackEditable
    }

    suspend fun scroll(direction: String): String {
        val service = require()
        val metrics = service.resources.displayMetrics
        val w = metrics.widthPixels; val h = metrics.heightPixels
        val ok = if (direction.equals("down", true)) {
            swipe(w / 2, (h * 0.75).toInt(), w / 2, (h * 0.25).toInt(), 400)
        } else {
            swipe(w / 2, (h * 0.25).toInt(), w / 2, (h * 0.75).toInt(), 400)
        }
        return if (ok) "Scrolled $direction" else "Scroll gesture failed"
    }

    fun globalAction(action: Int, name: String): String {
        val service = require()
        val ok = service.performGlobalAction(action)
        if (!ok) throw IllegalStateException("$name global action failed")
        return "$name performed"
    }

    /** Structured snapshot of the current screen (visible interactive elements). */
    suspend fun screenSnapshot(maxNodes: Int = 60): String {
        val service = require()
        return withContext(Dispatchers.Main) {
            val root = service.rootInActiveWindow ?: throw AccessibilityNotEnabled("No active window is accessible right now")
            val sb = StringBuilder()
            sb.appendLine("PACKAGE: ${root.packageName}")
            val queue = ArrayDeque<AccessibilityNodeInfo>().also { it.add(root) }
            var count = 0
            val bounds = Rect()
            while (queue.isNotEmpty() && count < maxNodes) {
                val n = queue.removeFirst()
                n.getBoundsInScreen(bounds)
                val visible = bounds.width() > 0 && bounds.height() > 0
                val interesting = n.text?.isNotBlank() == true || n.isClickable || n.isEditable ||
                    n.contentDescription?.isNotBlank() == true
                if (visible && interesting) {
                    count++
                    val flags = buildList {
                        if (n.isClickable) add("clickable")
                        if (n.isEditable) add("editable")
                        if (n.isScrollable) add("scrollable")
                    }.joinToString(",")
                    val id = n.viewIdResourceName ?: ""
                    sb.appendLine("[$count] ${n.className?.toString()?.substringAfterLast('.')} id=$id bounds=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) ${flags} text=\"${n.text}\" desc=\"${n.contentDescription ?: ""}\"")
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            if (count == 0) sb.appendLine("(no interactive elements found on screen)")
            sb.toString()
        }
    }
}
