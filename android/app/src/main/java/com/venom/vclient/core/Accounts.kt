package com.venom.vclient.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Account(
    val id: String,
    val uuid: String,
    val name: String,
    val type: String,
    val accessToken: String = "0",
    val createdAt: Long = System.currentTimeMillis()
)

class AccountsRepository(private val ctx: Context) {
    private val file: File = File(ctx.filesDir, "accounts.json")

    fun list(): List<Account> {
        val arr = Json.readArray(file)
        val out = mutableListOf<Account>()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            out.add(
                Account(
                    id = j.optString("id"),
                    uuid = j.optString("uuid"),
                    name = j.optString("name"),
                    type = j.optString("type", "offline"),
                    accessToken = j.optString("accessToken", "0"),
                    createdAt = j.optLong("createdAt", 0)
                )
            )
        }
        return out
    }

    fun addOffline(name: String): Account {
        val safe = name.trim()
        if (!Regex("^[A-Za-z0-9_]{3,24}$").matches(safe)) {
            throw IllegalStateException("username must be 3-24 chars (A-Z, 0-9, _)")
        }
        val uuid = NameHash.uuid(safe)
        val all = list()
        all.firstOrNull { it.uuid == uuid }?.let { return it }
        val acc = Account(id = "off-" + System.currentTimeMillis().toString(16), uuid = uuid, name = safe, type = "offline")
        writeAll(all + acc)
        return acc
    }

    fun addMicrosoft(uuid: String, name: String, token: String): Account {
        val acc = Account(
            id = "ms-" + System.currentTimeMillis().toString(16),
            uuid = uuid,
            name = name,
            type = "microsoft",
            accessToken = token
        )
        writeAll(list() .filterNot { it.uuid == uuid } + acc)
        return acc
    }

    fun remove(idOrUuid: String) {
        writeAll(list().filterNot { it.id == idOrUuid || it.uuid == idOrUuid })
    }

    private fun writeAll(items: List<Account>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("uuid", it.uuid)
                    put("name", it.name)
                    put("type", it.type)
                    put("accessToken", it.accessToken)
                    put("createdAt", it.createdAt)
                }
            )
        }
        Json.write(file, arr)
    }
}
