package com.venom.vclient.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getString(url: String): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("empty response")
            return body
        }
    }

    fun getJson(url: String): JSONObject = JSONObject(getString(url))

    /** Small reachability probe (short timeout). */
    fun isReachable(url: String): Boolean = try {
        val c = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()
        c.newCall(Request.Builder().url(url).head().build()).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }
}
