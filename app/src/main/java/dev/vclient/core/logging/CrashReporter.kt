package dev.vclient.core.logging

import android.os.Build
import dev.vclient.core.isolation.IsolatedPaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash protection, JVM side.
 *
 *  - [install] registers a default uncaught-exception handler that writes a
 *    full report (stack trace + device info + recent log lines) into the
 *    isolated crashes/ directory, then chains to the previous handler so the
 *    system still shows its own crash handling.
 *  - [recentCrashTimestamps] / [recordCrashTimestamp] maintain a small
 *    history file. Boot logic uses it to detect crash loops and start in
 *    Safe Mode (all modules disabled, overlay still usable).
 */
class CrashReporter(private val crashesDir: File, private val logger: VLogger) {

    @Serializable
    private data class History(val timestamps: List<Long> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val historyFile: File get() = File(crashesDir, "crash-history.json")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        logger.i(TAG, "Crash reporter installed")
    }

    fun writeReport(thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(crashesDir, "crash-$stamp.txt")
        file.writeText(buildReport(thread, throwable))
        recordCrashTimestamp(System.currentTimeMillis())
        logger.e(TAG, "Uncaught exception on ${thread.name}", throwable)
    }

    fun crashFiles(): List<File> =
        crashesDir.listFiles { f -> f.name.startsWith("crash-") }?.sortedByDescending { it.name } ?: emptyList()

    /** Returns true when the app crashed >= [count] times within [windowMs] (crash loop). */
    fun isCrashLoop(count: Int = 3, windowMs: Long = 10L * 60 * 1000): Boolean {
        val cutoff = System.currentTimeMillis() - windowMs
        return recentCrashTimestamps().count { it > cutoff } >= count
    }

    private fun recentCrashTimestamps(): List<Long> = readHistory().timestamps

    private fun recordCrashTimestamp(now: Long) {
        val stamps = (readHistory().timestamps + now).takeLast(20)
        IsolatedPaths.atomicWrite(historyFile, json.encodeToString(History.serializer(), History(stamps)))
    }

    private fun readHistory(): History {
        val text = IsolatedPaths.readTextOrNull(historyFile) ?: return History()
        return runCatching { json.decodeFromString(History.serializer(), text) }.getOrDefault(History())
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("V Client crash report")
        appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("Thread: ${thread.name}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine(throwable.stackTraceToString())
        appendLine()
        appendLine("--- recent log ---")
        logger.recentLines(120).forEach(::appendLine)
    }

    companion object {
        const val TAG = "CrashReporter"
    }
}
