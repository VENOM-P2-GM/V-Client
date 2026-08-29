package dev.vclient.core.game

import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Virtual keys surfaced by the Keystrokes HUD. */
enum class VKey(val id: String, val display: String) {
    W("w", "W"), A("a", "A"), S("s", "S"), D("d", "D"),
    JUMP("jump", "Jump"), SNEAK("sneak", "Sneak"), SPRINT("sprint", "Sprint");

    companion object { fun byId(id: String?): VKey? = entries.firstOrNull { it.id == id } }
}

/** A user-calibrated on-screen rectangle that maps a game touch to a [VKey]. */
data class KeyRegion(val key: VKey, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    override fun toString(): String = "${key.display}: ${Math.round(right - left)}x${Math.round(bottom - top)}"
}

data class InputSnapshot(
    val keys: Set<VKey> = emptySet(),
    val cpsLeft: Double = 0.0,
    val cpsRight: Double = 0.0,
    val peakCpsLeft: Double = 0.0,
    val peakCpsRight: Double = 0.0,
)

/**
 * Aggregates input signal for the Keystrokes and CPS Counter modules.
 *
 * Real data arrives from the (optional) accessibility service: on Android 14+
 * it forwards raw touchscreen motion events ([onMotion]). Users can calibrate
 * on-screen rectangles ("regions") that map game controls to virtual keys, so
 * Keystrokes reflects the actual on-screen buttons they press in Minecraft.
 * Without the service, the tracker still works with taps on V Client's own
 * surfaces and demo mode.
 */
object InputTracker {

    private val _keys = MutableStateFlow<Set<VKey>>(emptySet())
    val keys: StateFlow<Set<VKey>> get() = _keys

    /** Whether calibrated touch regions should drive key states. */
    @Volatile var mappingEnabled: Boolean = true

    private val _regions = MutableStateFlow<List<KeyRegion>>(emptyList())
    val regions: StateFlow<List<KeyRegion>> get() = _regions

    /** Set when the user is calibrating a region: next touch defines it. */
    @Volatile private var armedKey: VKey? = null
    private var captureStart: Pair<Float, Float>? = null
    @Volatile var onRegionRecorded: ((VKey, KeyRegion) -> Unit)? = null
    @Volatile var onRecordingCancelled: (() -> Unit)? = null

    private val leftTaps = ArrayDeque<Long>()
    private val rightTaps = ArrayDeque<Long>()
    private var peakLeft = 0.0
    private var peakRight = 0.0

    // pointerId -> key currently held inside a region
    private val pointerKeys = HashMap<Int, VKey>()

    fun isPressed(key: VKey): Boolean = _keys.value.contains(key)

    @Synchronized
    fun setKey(key: VKey, pressed: Boolean) {
        val current = _keys.value
        _keys.value = if (pressed) current + key else current - key
    }

    @Synchronized
    fun clearAllKeys() {
        _keys.value = emptySet()
        pointerKeys.clear()
    }

    // --- CPS ------------------------------------------------------------------

    @Synchronized
    fun recordTap(right: Boolean) {
        val now = System.currentTimeMillis()
        (if (right) rightTaps else leftTaps).addLast(now)
    }

    @Synchronized
    fun cpsLeft(): Double = prune(leftTaps).also { if (it > peakLeft) peakLeft = it }

    @Synchronized
    fun cpsRight(): Double = prune(rightTaps).also { if (it > peakRight) peakRight = it }

    @Synchronized
    fun snapshot(): InputSnapshot {
        val l = prune(leftTaps).also { if (it > peakLeft) peakLeft = it }
        val r = prune(rightTaps).also { if (it > peakRight) peakRight = it }
        return InputSnapshot(_keys.value, l, r, peakLeft, peakRight)
    }

    @Synchronized
    fun resetPeaks() {
        peakLeft = 0.0; peakRight = 0.0
    }

    private fun prune(taps: ArrayDeque<Long>): Double {
        val cutoff = System.currentTimeMillis() - 1000
        while (taps.isNotEmpty() && taps.first() < cutoff) taps.removeFirst()
        return taps.size.toDouble()
    }

    // --- Region management ------------------------------------------------------

    @Synchronized
    fun setRegions(list: List<KeyRegion>) {
        _regions.value = list
    }

    fun regionFor(key: VKey): KeyRegion? = _regions.value.firstOrNull { it.key == key }

    /** Arms region calibration: the next DOWN..UP touch on the screen defines the rect. */
    fun armRecording(key: VKey) {
        armedKey = key
        captureStart = null
    }

    @Synchronized
    fun cancelRecording() {
        armedKey = null
        captureStart = null
        onRecordingCancelled?.invoke()
    }

    val isRecordingArmed: Boolean get() = armedKey != null

    // --- Raw motion (forwarded by VAccessibilityService on Android 14+) ----------

    @Synchronized
    fun onMotion(event: MotionEvent, screenWidth: Float) {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val x = event.getX(i); val y = event.getY(i)
                val pid = event.getPointerId(i)
                val armed = armedKey
                if (armed != null) {
                    captureStart = x to y
                    return
                }
                recordTap(right = x > screenWidth * 0.5f)
                if (mappingEnabled) {
                    _regions.value.firstOrNull { it.contains(x, y) }?.let { region ->
                        pointerKeys[pid] = region.key
                        setKey(region.key, true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pid = event.getPointerId(event.actionIndex)
                val armed = armedKey
                val start = captureStart
                if (armed != null && start != null && action != MotionEvent.ACTION_CANCEL) {
                    val i = event.actionIndex
                    val rect = KeyRegion(
                        key = armed,
                        left = minOf(start.first, event.getX(i)),
                        top = minOf(start.second, event.getY(i)),
                        right = maxOf(start.first, event.getX(i)),
                        bottom = maxOf(start.second, event.getY(i)),
                    )
                    if (rect.right - rect.left >= 12f && rect.bottom - rect.top >= 12f) {
                        setRegions(_regions.value.filterNot { it.key == armed } + rect)
                        armedKey = null
                        captureStart = null
                        onRegionRecorded?.invoke(armed, rect)
                    }
                    return
                }
                pointerKeys.remove(pid)?.let { key ->
                    if (pointerKeys.values.none { it == key }) setKey(key, false)
                }
            }
        }
    }
}
