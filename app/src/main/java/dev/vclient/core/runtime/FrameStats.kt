package dev.vclient.core.runtime

/**
 * Per-frame performance snapshot, produced by the native runtime (FpsStats)
 * or by the Kotlin fallback calculator when the native library is unavailable.
 */
data class FrameStats(
    val fps: Double,
    val avgFrameMs: Double,
    val p99FrameMs: Double,
    val jitterMs: Double,
    val droppedFrames: Long,
)

/** Pure-Kotlin mirror of the native FpsStats; used as a graceful fallback. */
class FrameStatsCalculator(private val window: Int = 240) {

    private val dt = DoubleArray(window)
    private var idx = 0
    private var count = 0
    private var emaDt = 16.7
    private var dropped = 0L

    fun push(dtMs: Double): FrameStats {
        val clamped = dtMs.coerceIn(0.1, 250.0)
        dt[idx] = clamped
        idx = (idx + 1) % window
        if (count < window) count++
        emaDt = emaDt * 0.9 + clamped * 0.1
        if (clamped > 34.0) dropped++

        val active = dt.copyOf(count)
        val avg = active.average()
        val sorted = active.sorted()
        val p99 = sorted[((sorted.size - 1) * 0.99).toInt().coerceIn(0, sorted.size - 1)]
        val jitter = if (count > 1) kotlin.math.sqrt(active.map { (it - avg) * (it - avg) }.average()) else 0.0
        return FrameStats(
            fps = 1000.0 / emaDt.coerceAtLeast(0.1),
            avgFrameMs = avg,
            p99FrameMs = p99,
            jitterMs = jitter,
            droppedFrames = dropped,
        )
    }
}
