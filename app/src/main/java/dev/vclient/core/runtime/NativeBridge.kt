package dev.vclient.core.runtime

import dev.vclient.core.logging.VLogger

/**
 * Kotlin entry point into the C++20 native runtime (libvclient.so).
 *
 * The client degrades gracefully when the library is missing (e.g. a build
 * without NDK): [available] is false and every wrapper returns a safe
 * fallback, with pure-Kotlin replacements covering frame statistics.
 */
object NativeBridge {

    val available: Boolean = try {
        System.loadLibrary("vclient")
        true
    } catch (_: Throwable) {
        false
    }

    // --- raw JNI surface (implemented in cpp/vclient_jni.cpp) -----------------

    external fun nativeInit(rootDir: String): Boolean
    external fun nativeVersion(): String
    external fun nativeLog(level: Int, tag: String, message: String)
    external fun nativeInstallCrashGuard(): Boolean
    /** Returns [fps, avgFrameMs, p99FrameMs, jitterMs, droppedFrames]. */
    external fun nativeFrame(dtMs: Double): DoubleArray
    /** Returns [onScreen(0|1), screenX, screenY, distance, bearingDeg]. */
    external fun nativeWaypoint(
        px: Double, py: Double, pz: Double, yawDeg: Double, pitchDeg: Double,
        tx: Double, ty: Double, tz: Double, fovHDeg: Double, screenW: Double, screenH: Double,
    ): FloatArray
    external fun nativeMotionBlurSample(gyroX: Double, gyroY: Double, dtMs: Double): Float
    external fun nativeMotionBlurConfigure(sensitivity: Float, maxRadiusDp: Float)
    external fun nativeBridgeStatus(): String
    external fun nativeShutdown()

    // --- friendly wrappers ------------------------------------------------------

    fun initialize(rootDir: String, logger: VLogger): Boolean {
        if (!available) {
            logger.w(TAG, "Native runtime unavailable — using Kotlin fallbacks")
            return false
        }
        val ok = runCatching { nativeInit(rootDir) }.getOrElse {
            logger.e(TAG, "nativeInit failed", it)
            false
        }
        if (ok) {
            val crashGuard = runCatching { nativeInstallCrashGuard() }.getOrDefault(false)
            logger.i(TAG, "Native runtime ready: ${version()} (crashGuard=$crashGuard)")
        }
        return ok
    }

    fun version(): String =
        if (available) runCatching { nativeVersion() }.getOrDefault("unknown") else "kotlin-fallback"

    fun frameStats(dtMs: Double): FrameStats? {
        if (!available) return null
        val a = runCatching { nativeFrame(dtMs) }.getOrNull() ?: return null
        if (a.size < 5) return null
        return FrameStats(
            fps = a[0],
            avgFrameMs = a[1],
            p99FrameMs = a[2],
            jitterMs = a[3],
            droppedFrames = a[4].toLong(),
        )
    }

    data class WaypointProjection(
        val onScreen: Boolean,
        val screenX: Float,
        val screenY: Float,
        val distance: Double,
        val bearingDeg: Float,
    )

    fun waypoint(
        px: Double, py: Double, pz: Double, yawDeg: Double, pitchDeg: Double,
        tx: Double, ty: Double, tz: Double, fovHDeg: Double, screenW: Float, screenH: Float,
    ): WaypointProjection? {
        if (!available) return null
        val a = runCatching {
            nativeWaypoint(px, py, pz, yawDeg, pitchDeg, tx, ty, tz, fovHDeg, screenW.toDouble(), screenH.toDouble())
        }.getOrNull() ?: return null
        if (a.size < 5) return null
        return WaypointProjection(a[0] > 0.5f, a[1], a[2], a[3].toDouble(), a[4])
    }

    /** Desired motion-blur radius in dp given angular velocity (rad/s). */
    fun motionBlurRadius(gyroX: Double, gyroY: Double, dtMs: Double): Float =
        if (available) runCatching { nativeMotionBlurSample(gyroX, gyroY, dtMs) }.getOrDefault(0f) else 0f

    fun motionBlurConfigure(sensitivity: Float, maxRadiusDp: Float) {
        if (available) runCatching { nativeMotionBlurConfigure(sensitivity, maxRadiusDp) }
    }

    fun bridgeStatus(): String =
        if (available) runCatching { nativeBridgeStatus() }.getOrDefault("unknown") else "native runtime unavailable"

    fun shutdown() {
        if (available) runCatching { nativeShutdown() }
    }

    const val TAG = "NativeBridge"
}
