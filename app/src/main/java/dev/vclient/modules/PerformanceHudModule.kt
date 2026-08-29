package dev.vclient.modules

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.VClientCore
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudTexts
import dev.vclient.core.module.HudModule
import dev.vclient.core.module.TickContext
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.settingOf
import java.io.File
import java.util.Locale

/**
 * Performance HUD — device-level telemetry sampled on the slow tick:
 * CPU load (/proc/stat deltas), RAM (ActivityManager), battery
 * (temperature/level via BatteryManager) and frame statistics.
 */
class PerformanceHudModule : HudModule(ID, NAME, "CPU, memory, battery and frame statistics.") {

    private val showCpu by settingOf(BoolSetting("show_cpu", "CPU", true))
    private val showRam by settingOf(BoolSetting("show_ram", "Memory", true))
    private val showBattery by settingOf(BoolSetting("show_battery", "Battery", true))
    private val showFrames by settingOf(BoolSetting("show_frames", "Frame stats", true))

    @Volatile private var cpuPercent: Int? = null
    @Volatile private var ramPercent: Int? = null
    @Volatile private var appMemMb: Int? = null
    @Volatile private var batteryTemp: Float? = null
    @Volatile private var batteryLevel: Int? = null

    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L

    override fun onTick(ctx: TickContext) {
        if (!ctx.slowTick) return
        val context = VClientCore.core.appContext
        if (showCpu) cpuPercent = sampleCpu()
        if (showRam) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val info = ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                ramPercent = if (info.totalMem > 0) {
                    ((info.totalMem - info.availMem) * 100 / info.totalMem).toInt()
                } else null
            }
            appMemMb = (android.os.Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)).toInt()
        }
        if (showBattery) {
            val bm = context.getSystemService(BatteryManager::class.java)
            if (bm != null) {
                val temp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE)
                if (temp != Int.MIN_VALUE) batteryTemp = temp / 10f
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (level != Int.MIN_VALUE) batteryLevel = level
            }
        }
    }

    override val hud = Element()

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF =
            HudTexts.measureSized(frame, lines(frame), textSizePx(frame.density))

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            HudTexts.drawColored(
                canvas, frame, rect,
                lines(frame).map { it to textColorValue() },
                textSizePx(frame.density), textShadowEnabled(),
            )
        }

        private fun lines(frame: HudFrame): List<String> {
            val out = mutableListOf<String>()
            if (showCpu) out.add(cpuPercent?.let { "CPU $it%" } ?: "CPU —")
            if (showRam) {
                val ram = ramPercent?.let { "RAM $it%" } ?: "RAM —"
                val app = appMemMb?.let { " · App ${it}MB" } ?: ""
                out.add(ram + app)
            }
            if (showBattery) {
                val temp = batteryTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "—"
                val level = batteryLevel?.let { "$it%" } ?: "—"
                out.add("BAT $level · $temp")
            }
            if (showFrames) {
                val stats = frame.stats
                if (stats != null) {
                    out.add(String.format(Locale.US, "%.0f FPS · %d drop", stats.fps, stats.droppedFrames))
                } else {
                    out.add("FPS —")
                }
            }
            return out
        }
    }

    /** Global CPU usage from /proc/stat deltas between slow ticks. */
    private fun sampleCpu(): Int? = runCatching {
        val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return null
        val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return null
        val idle = parts[3] + (parts.getOrNull(4) ?: 0L) // idle + iowait
        val total = parts.sum()
        val dTotal = total - lastCpuTotal
        val dIdle = idle - lastCpuIdle
        lastCpuTotal = total
        lastCpuIdle = idle
        if (dTotal <= 0) null else ((dTotal - dIdle) * 100 / dTotal).toInt().coerceIn(0, 100)
    }.getOrNull()

    companion object {
        const val ID = "performance_hud"
        const val NAME = "Performance HUD"
    }
}
