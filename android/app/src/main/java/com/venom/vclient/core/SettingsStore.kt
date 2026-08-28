package com.venom.vclient.core

import android.content.Context
import org.json.JSONObject
import java.io.File

class SettingsStore(private val ctx: Context) {
    var defaultMemoryMax: Int = 4096
        private set
    var defaultMemoryMin: Int = 1024
        private set
    var assetMode: String = "shared"
        private set
    var msClientId: String = ""
        private set
    var language: String = "ar"
        private set
    var theme: String = "venom"
        private set

    private val file: File = File(ctx.filesDir, "settings.json")

    init {
        val j = Json.read(file) ?: return
        defaultMemoryMax = j.optInt("defaultMemoryMax", defaultMemoryMax)
        defaultMemoryMin = j.optInt("defaultMemoryMin", defaultMemoryMin)
        assetMode = j.optString("assetMode", assetMode)
        msClientId = j.optString("msClientId", msClientId)
        language = j.optString("language", language)
        theme = j.optString("theme", theme)
    }

    fun update(
        memoryMax: Int? = null,
        memoryMin: Int? = null,
        assetMode: String? = null,
        msClientId: String? = null,
        language: String? = null,
        theme: String? = null
    ) {
        memoryMax?.let { defaultMemoryMax = it.coerceIn(512, 32768) }
        memoryMin?.let { defaultMemoryMin = it.coerceIn(256, 16384) }
        assetMode?.let { if (it == "shared" || it == "isolated") this.assetMode = it }
        msClientId?.let { this.msClientId = it }
        language?.let { if (it == "ar" || it == "en") this.language = it }
        theme?.let { if (it == "venom" || it == "toxic") this.theme = it }
        Json.write(
            file,
            JSONObject().apply {
                put("defaultMemoryMax", defaultMemoryMax)
                put("defaultMemoryMin", defaultMemoryMin)
                put("assetMode", assetMode)
                put("msClientId", msClientId)
                put("language", language)
                put("theme", theme)
            }
        )
    }

    fun dataRoot(): String = File(ctx.filesDir, "profiles").absolutePath
}
