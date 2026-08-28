package com.venom.vclient.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Json {
    fun read(file: File): JSONObject? = try {
        JSONObject(file.readText())
    } catch (e: Exception) {
        null
    }

    fun readArray(file: File): JSONArray = try {
        JSONArray(file.readText())
    } catch (e: Exception) {
        JSONArray()
    }

    fun write(file: File, data: Any) {
        try {
            file.parentFile?.mkdirs()
            val text = when (data) {
                is JSONObject -> data.toString(2)
                is JSONArray -> data.toString(2)
                else -> data.toString()
            }
            file.writeText(text)
        } catch (e: Exception) {
            // best effort persistence
        }
    }
}
