package com.venom.vclient.core

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object Downloader {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun sha1File(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun isSatisfied(dest: File, size: Long = 0, sha1: String? = null, verify: Boolean = false): Boolean {
        if (!dest.isFile) return false
        if (size > 0 && dest.length() != size) return false
        if (sha1 != null && verify && sha1File(dest) != sha1) return false
        return true
    }

    /**
     * Stream a file to [dest] with resume support (.part temp), retries,
     * sha1 verification and progress callback (fraction 0..1).
     */
    fun download(
        url: String,
        dest: File,
        size: Long = 0,
        sha1: String? = null,
        onProgress: ((Float) -> Unit)? = null,
        retries: Int = 2
    ) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        var attempt = 0
        while (true) {
            var resume = if (tmp.isFile) tmp.length() else 0L
            if (size > 0 && resume >= size) resume = 0
            val reqBuilder = Request.Builder().url(url)
            if (resume > 0) reqBuilder.header("Range", "bytes=$resume-")
            try {
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) throw IOException("HTTP ${resp.code}")
                    if (resp.code == 200) resume = 0
                    val body = resp.body ?: throw IOException("empty body")
                    val remaining = body.contentLength()
                    val total = when {
                        size > 0 -> size
                        resume > 0 -> resume + remaining
                        else -> if (remaining > 0) remaining else 0L
                    }
                    body.byteStream().use { ins ->
                        FileOutputStream(tmp, resume > 0).use { outs ->
                            val buf = ByteArray(16384)
                            var got = resume
                            while (true) {
                                val n = ins.read(buf)
                                if (n <= 0) break
                                outs.write(buf, 0, n)
                                got += n
                                if (total > 0) onProgress?.invoke(minOf(1f, got.toFloat() / total))
                            }
                        }
                    }
                }
                if (sha1 != null) {
                    val actual = sha1File(tmp)
                    if (actual != sha1) {
                        tmp.delete()
                        throw IOException("sha1 mismatch")
                    }
                }
                tmp.renameTo(dest)
                onProgress?.invoke(1f)
                return
            } catch (e: Exception) {
                if (attempt >= retries) throw e
                attempt++
                try {
                    Thread.sleep(800L * attempt)
                } catch (_: InterruptedException) {
                }
            }
        }
    }
}
