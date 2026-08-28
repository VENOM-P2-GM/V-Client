package com.venom.vclient.core.engine

import com.venom.vclient.core.EnvPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * V-Engine — the built-in demo game engine of V Client.
 * Runs strictly inside an isolated profile environment: it only reads/writes
 * the profile directory handed over by the launcher pipeline.
 */
class VEngine(
    private val env: EnvPaths,
    private val playerName: String,
    private val playerUuid: String,
    private val engineVersion: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val rnd = Random(1337)
    private val mobsList = listOf("creeper", "zombie", "skeleton", "spider", "enderman", "slime")

    private var lastTick = 0
    private var lastMobs = 2
    private var lastChunks = 48

    fun start(onLog: (String) -> Unit, onExit: (Int) -> Unit) {
        if (job != null) return
        job = scope.launch {
            var code = 0
            try {
                onLog("")
                onLog("┌" + "─".repeat(56) + "┐")
                onLog(("│  V-ENGINE  v" + engineVersion + "  ·  V Client isolated runtime").padEnd(56) + "│")
                onLog("└" + "─".repeat(56) + "┘")
                onLog("[V-Engine] profile env : " + env.root.absolutePath)
                onLog("[V-Engine] session     : $playerName [$playerUuid]")

                val mods = env.mods.listFiles()
                    ?.filter { it.name.endsWith(".jar") && !it.name.endsWith(".disabled") }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                if (mods.isEmpty()) {
                    onLog("[MOD]     no mods in this environment")
                } else {
                    onLog("[MOD]     ${mods.size} mod(s) loaded from the isolated mods/ directory")
                    for (m in mods) onLog("[MOD]     loaded: ${m.name}")
                }

                onLog("[World]   generating Overworld (seed 1337) ... done")
                onLog("[World]   48 chunks loaded, spawn located")

                var tick = 0
                var mobs = 2
                var chunks = 48
                var weather = "clear"
                while (isActive) {
                    delay(50)
                    tick++
                    lastTick = tick
                    lastMobs = mobs
                    lastChunks = chunks
                    if (tick % 40 == 0) {
                        val r = rnd.nextFloat()
                        when {
                            r < 0.35f && mobs < 12 -> {
                                mobs++
                                onLog("[World]   " + mobsList.random() + " spawned")
                            }
                            r < 0.50f && mobs > 1 -> {
                                mobs--
                                onLog("[World]   a mob despawned")
                            }
                            r < 0.65f -> {
                                chunks++
                                onLog("[World]   chunk $chunks streamed in")
                            }
                            r < 0.70f -> onLog("[Player]  $playerName placed oak_log x1")
                            r < 0.75f -> {
                                weather = if (weather == "clear") "rain" else "clear"
                                onLog("[World]   weather → $weather")
                            }
                            else -> {}
                        }
                    }
                    if (tick % 100 == 0) {
                        onLog("[perf]    fps=${234 + rnd.nextInt(13)} tick=$tick mobs=$mobs chunks=$chunks weather=$weather")
                    }
                    if (tick % 200 == 0) {
                        save(tick, mobs, chunks)
                        onLog("[Save]    world saved → saves/world.dat ($tick ticks)")
                    }
                }
            } catch (e: Exception) {
                onLog("[V-Engine] error: ${e.message}")
                code = 1
            }
            save(lastTick, lastMobs, lastChunks)
            onLog("")
            onLog("[V-Engine] shutting down gracefully")
            onLog("[V-Engine] session closed. the environment stays isolated and intact.")
            onExit(code)
        }
    }

    private fun save(tick: Int, mobs: Int, chunks: Int) {
        try {
            val f = File(env.saves, "world.dat")
            f.parentFile?.mkdirs()
            f.writeText(
                JSONObject().apply {
                    put("tick", tick)
                    put("mobs", mobs)
                    put("chunks", chunks)
                    put(
                        "players",
                        JSONArray().put(
                            JSONObject().apply {
                                put("name", playerName)
                                put("uuid", playerUuid)
                            }
                        )
                    )
                    put("savedAt", System.currentTimeMillis())
                }.toString(2)
            )
        } catch (_: Exception) {
        }
    }

    fun stop() {
        job?.cancel()
    }
}
