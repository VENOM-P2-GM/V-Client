package com.venom.vclient.core

import java.security.MessageDigest

/** MultiMC-style offline (name-hash) v3 UUID — the standard for offline accounts. */
object NameHash {
    fun uuid(name: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(name.toByteArray())
        d[6] = (d[6].toInt() and 0x0f or 0x30).toByte()
        d[8] = (d[8].toInt() and 0x3f or 0x80).toByte()
        val hex = d.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
