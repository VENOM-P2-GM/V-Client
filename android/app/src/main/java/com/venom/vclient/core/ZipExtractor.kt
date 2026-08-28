package com.venom.vclient.core

import java.io.File
import java.util.zip.ZipFile

object ZipExtractor {
    /** Extract a zip into [dest], skipping [exclude] prefixes (e.g. "META-INF/"). */
    fun extract(zip: File, dest: File, exclude: List<String> = listOf("META-INF/")) {
        ZipFile(zip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                if (exclude.any { e.name.startsWith(it) }) continue
                val out = File(dest, e.name)
                if (!out.canonicalFile.path.startsWith(dest.canonicalFile.path)) continue
                out.parentFile?.mkdirs()
                zf.getInputStream(e).use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                }
            }
        }
    }
}
