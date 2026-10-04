package com.agentos.app.data.termux

import android.content.Context
import android.content.Intent
import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Real Termux integration using the documented RUN_COMMAND intent API.
 *
 * Requirements on the device (documented in README):
 * 1. Termux installed (F-Droid build recommended).
 * 2. In Termux: ~/.termux/termux.properties must contain `allow-external-apps=true`,
 *    then restart Termux.
 * 3. The command runs in Termux's own sandbox; results (stdout/stderr/exit_code)
 *    are written by Termux into a result directory this app provides, and we
 *    poll for them.
 *
 * If Termux is not configured, [execute] fails with an actionable error —
 * it never simulates output.
 */
class TermuxBridge(private val context: Context) {

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_RESULT_DIRECTORY = "com.termux.RUN_COMMAND_RESULT_DIRECTORY"
        const val EXTRA_RESULT_BASENAME = "com.termux.RUN_COMMAND_RESULT_FILE_BASENAME"
        const val TERMUX_SH = "/data/data/com.termux/files/usr/bin/sh"
        const val TAG = "TermuxBridge"
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
    }.isSuccess

    fun version(): String? = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0).versionName
    }.getOrNull()

    data class ExecResult(
        val exitCode: Int?,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
        val transport: String = "termux"
    ) {
        fun success() = exitCode == 0
    }

    /**
     * Executes a shell command inside Termux and captures stdout/stderr/exit code.
     */
    suspend fun execute(
        command: String,
        workdir: String? = null,
        timeoutMs: Long = 60_000L
    ): ExecResult = withContext(Dispatchers.IO) {
        if (!isInstalled()) throw TermuxUnavailable("Termux is not installed on this device")
        val resultDir = File(context.cacheDir, "termux_results/${System.currentTimeMillis()}")
        resultDir.mkdirs()
        val baseName = "result"

        val intent = Intent(RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.RunCommandService")
            putExtra(EXTRA_PATH, TERMUX_SH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
            workdir?.let { putExtra(EXTRA_WORKDIR, it) }
            putExtra(EXTRA_BACKGROUND, false)
            putExtra(EXTRA_RESULT_DIRECTORY, resultDir.absolutePath)
            putExtra(EXTRA_RESULT_BASENAME, baseName)
        }
        Logger.i(TAG, "Sending RUN_COMMAND (${command.take(80)}) resultDir=${resultDir.name}")
        val started = System.currentTimeMillis()
        val sendOk = runCatching {
            context.startService(intent)
        }.map { true }.getOrElse {
            Logger.e(TAG, "Failed to start Termux RunCommandService", it)
            false
        }
        if (!sendOk) throw TermuxUnavailable("Could not start Termux RunCommandService — is Termux installed and running?")

        val stdoutFile = File(resultDir, "$baseName.stdout")
        val stderrFile = File(resultDir, "$baseName.stderr")
        val exitFile = File(resultDir, "$baseName.exit_code")

        val finished = withTimeoutOrNull(timeoutMs) {
            while (!exitFile.exists()) delay(250)
            true
        } ?: false

        if (!finished) {
            ExecResult(null, readFileSafe(stdoutFile), readFileSafe(stderrFile), timedOut = true)
        } else {
            // stdout/stderr may still be flushing; small grace period
            var attempts = 0
            while (attempts < 10 && !stdoutFile.exists() && !stderrFile.exists()) { delay(150); attempts++ }
            delay(200)
            val code = exitFile.readText().trim().toIntOrNull()
            ExecResult(code, readFileSafe(stdoutFile), readFileSafe(stderrFile), timedOut = false)
        }.also { Logger.i(TAG, "Termux exec done exit=${it.exitCode} in ${System.currentTimeMillis() - started}ms") }
    }

    suspend fun testConnection(): String {
        if (!isInstalled()) throw TermuxUnavailable("Termux is not installed")
        val res = execute("echo agentos-termux-ok && \$PREFIX/bin/uname -o", timeoutMs = 15_000)
        return when {
            res.timedOut -> throw TermuxUnavailable(
                "Termux did not return results. Check that ~/.termux/termux.properties contains allow-external-apps=true and Termux was restarted."
            )
            res.success() -> "Termux connected. ${res.stdout.trim()}"
            else -> throw TermuxUnavailable("Termux responded with exit code ${res.exitCode}. stderr: ${res.stderr.take(200)}")
        }
    }

    private fun readFileSafe(f: File): String =
        runCatching { f.readText() }.getOrDefault("")

    class TermuxUnavailable(message: String) : Exception(message)
}
