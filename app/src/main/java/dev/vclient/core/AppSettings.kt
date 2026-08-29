package dev.vclient.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Small session/app-level settings (kept in our own private SharedPreferences
 * file — still fully inside V Client's sandbox, never shared with Minecraft).
 * Everything module- or layout-related lives in profiles instead.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vclient_settings", Context.MODE_PRIVATE)

    var activeProfileName: String
        get() = prefs.getString(K_ACTIVE_PROFILE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE
        set(value) = edit { putString(K_ACTIVE_PROFILE, value) }

    /** "manual" | "demo" | "bridge" — see DataSourceKind. */
    var dataSourceKind: String
        get() = prefs.getString(K_DATA_SOURCE, "manual") ?: "manual"
        set(value) = edit { putString(K_DATA_SOURCE, value) }

    var manualX: Float
        get() = prefs.getFloat(K_MANUAL_X, 0f)
        set(value) = edit { putFloat(K_MANUAL_X, value) }
    var manualY: Float
        get() = prefs.getFloat(K_MANUAL_Y, 64f)
        set(value) = edit { putFloat(K_MANUAL_Y, value) }
    var manualZ: Float
        get() = prefs.getFloat(K_MANUAL_Z, 0f)
        set(value) = edit { putFloat(K_MANUAL_Z, value) }
    var manualYaw: Float
        get() = prefs.getFloat(K_MANUAL_YAW, 0f)
        set(value) = edit { putFloat(K_MANUAL_YAW, value) }
    var manualDimension: String
        get() = prefs.getString(K_MANUAL_DIM, "OVERWORLD") ?: "OVERWORLD"
        set(value) = edit { putString(K_MANUAL_DIM, value) }

    var hideOutsideGame: Boolean
        get() = prefs.getBoolean(K_HIDE_OUTSIDE, true)
        set(value) = edit { putBoolean(K_HIDE_OUTSIDE, value) }

    var vmenuBlurBackdrop: Boolean
        get() = prefs.getBoolean(K_VMENU_BLUR, true)
        set(value) = edit { putBoolean(K_VMENU_BLUR, value) }

    var bubbleX: Float
        get() = prefs.getFloat(K_BUBBLE_X, Float.MIN_VALUE)
        set(value) = edit { putFloat(K_BUBBLE_X, value) }
    var bubbleY: Float
        get() = prefs.getFloat(K_BUBBLE_Y, Float.MIN_VALUE)
        set(value) = edit { putFloat(K_BUBBLE_Y, value) }

    /** Set once a crash loop was detected; cleared after the user acknowledges. */
    var safeModeAcknowledged: Boolean
        get() = prefs.getBoolean(K_SAFE_MODE_ACK, false)
        set(value) = edit { putBoolean(K_SAFE_MODE_ACK, value) }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    companion object {
        const val DEFAULT_PROFILE = "Default"
        private const val K_ACTIVE_PROFILE = "active_profile"
        private const val K_DATA_SOURCE = "data_source"
        private const val K_MANUAL_X = "manual_x"
        private const val K_MANUAL_Y = "manual_y"
        private const val K_MANUAL_Z = "manual_z"
        private const val K_MANUAL_YAW = "manual_yaw"
        private const val K_MANUAL_DIM = "manual_dim"
        private const val K_HIDE_OUTSIDE = "hide_outside_game"
        private const val K_VMENU_BLUR = "vmenu_blur"
        private const val K_BUBBLE_X = "bubble_x"
        private const val K_BUBBLE_Y = "bubble_y"
        private const val K_SAFE_MODE_ACK = "safe_mode_ack"
    }
}
