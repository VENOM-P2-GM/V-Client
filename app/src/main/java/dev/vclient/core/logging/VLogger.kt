package dev.vclient.core.logging

import dev.vclient.core.isolation.IsolatedPaths
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Leveled, thread-safe file logger.
 *
 *  - One file per day: logs/app-YYYYMMDD.log, rotated at [MAX_FILE_BYTES].
 *  - Mirror of every message goes to logcat for development builds.
 *  - A bounded in-memory ring buffer backs the in-app log viewer and is
 *    dumped into crash reports for context.
 */
class VLogger(private val logsDir: File) {

    enum class Level(val tag: String) { DEBUG("D"), INFO("I"), WARN("W"), ERROR("E") }

    private val ring = ArrayDeque<String>(RING_SIZE)
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    private var currentFile: File? = null
    private var currentBytes = 0L
    @Volatile var minLevel: Level = Level.DEBUG
    @Volatile var alsoLogcat: Boolean = true

    @Synchronized
    fun log(level: Level, tag: String, message: String, tr: Throwable? = null) {
        if (level.ordinal < minLevel.ordinal) return
        val line = buildString {
            append(fmt.format(Date())).append(' ').append(level.tag)
            append(" [").append(tag).append("] ").append(message)
            tr?.let { append("\n").append(android.util.Log.getStackTraceString(it)) }
        }
        pushRing(line)
        writeLine(line)
        if (alsoLogcat) {
            val priority = when (level) {
                Level.DEBUG -> android.util.Log.DEBUG
                Level.INFO -> android.util.Log.INFO
                Level.WARN -> android.util.Log.WARN
                Level.ERROR -> android.util.Log.ERROR
            }
            android.util.Log.println(priority, "VClient/$tag", message + (tr?.let { "\n${android.util.Log.getStackTraceString(it)}" } ?: ""))
        }
    }

    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Level.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Level.ERROR, tag, msg, tr)

    @Synchronized
    fun recentLines(max: Int = RING_SIZE): List<String> = ring.toList().takeLast(max)

    fun logFiles(): List<File> =
        logsDir.listFiles { f -> f.name.startsWith("app-") }?.sortedByDescending { it.name } ?: emptyList()

    private fun pushRing(line: String) {
        if (ring.size >= RING_SIZE) ring.removeFirst()
        ring.addLast(line)
    }

    private fun writeLine(line: String) {
        runCatching {
            val file = rotateIfNeeded()
            file.appendText(line + "\n")
            currentBytes += line.length + 1
        }
    }

    private fun rotateIfNeeded(): File {
        val existing = currentFile
        if (existing != null && currentBytes < MAX_FILE_BYTES) return existing
        val file = File(logsDir, "app-${dayFmt.format(Date())}.log")
        if (file.exists() && file.length() > MAX_FILE_BYTES) {
            val rotated = File(logsDir, file.name + ".1")
            rotated.delete()
            file.renameTo(rotated)
        }
        currentFile = file
        currentBytes = file.length()
        return file
    }

    companion object {
        const val RING_SIZE = 500
        const val MAX_FILE_BYTES = 1L shl 21 // 2 MiB

        fun from(paths: IsolatedPaths): VLogger = VLogger(paths.logs)
    }
}
