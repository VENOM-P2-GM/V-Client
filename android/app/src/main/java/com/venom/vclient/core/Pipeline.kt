package com.venom.vclient.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class JobState(
    val id: String = "",
    val profileId: String = "",
    val engine: String = "vengine",
    val version: String = "1.1.0",
    val status: String = "queued",
    val stage: String = "queued",
    val stageMsg: String = "",
    val progress: Int = 0,
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val logTail: List<String> = emptyList()
)

class Job(val id: String, val profileId: String, val engine: String, val version: String) {
    val state = MutableStateFlow(JobState(id = id, profileId = profileId, engine = engine, version = version))
    val lines = ArrayDeque<String>()
    var cancelled = false
        private set
    private var engine: com.venom.vclient.core.engine.VEngine? = null

    fun log(line: String) {
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > 3000) lines.removeFirst()
        }
        val tail = synchronized(lines) { lines.takeLast(200) }
        state.update { it.copy(logTail = tail) }
    }

    fun stage(stageName: String, pct: Int, msg: String = "") {
        state.update { it.copy(stage = stageName, progress = pct.coerceIn(0, 100), stageMsg = msg) }
    }

    fun status(s: String) {
        state.update { cur ->
            cur.copy(
                status = s,
                finishedAt = if (s in FINISHED) System.currentTimeMillis() else cur.finishedAt
            )
        }
        Pipeline.emit()
    }

    fun finish(s: String) {
        status(s)
    }

    fun markCancelled() {
        cancelled = true
    }

    fun setEngine(e: com.venom.vclient.core.engine.VEngine) {
        engine = e
    }

    fun stopEngine() {
        engine?.stop()
    }

    companion object {
        val FINISHED = listOf("stopped", "error", "cancelled", "dryrun", "ready")
        val ACTIVE = listOf("queued", "preparing", "launching", "running")
    }
}

/**
 * The launch pipeline: one in-memory job per session.
 * vengine  → prepares the isolated env, writes the session, runs the built-in engine.
 * minecraft→ full Mojang pipeline (manifest → version → libraries → client jar →
 *            assets → natives → session → launch command) then hands the command
 *            to Termux (the Android runtime for Minecraft Java).
 */
object Pipeline {
    private val jobs = mutableListOf<Job>()
    private val _states = MutableStateFlow<List<JobState>>(emptyList())
    val states: StateFlow<List<JobState>> = _states.asStateFlow()

    fun emit() {
        _states.value = jobs.map { it.state.value }
    }

    fun activeFor(profileId: String): JobState? =
        _states.value.firstOrNull { it.profileId == profileId && it.status in Job.ACTIVE }

    fun start(ctx: Context, profile: Profile, account: Account, version: String): Job {
        if (activeFor(profile.id) != null) throw IllegalStateException("profile already has an active session")
        val job = Job("job-" + System.currentTimeMillis().toString(16), profile.id, profile.engine, version)
        jobs.add(job)
        emit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                run(ctx.applicationContext, job, profile, account, version)
            } catch (e: Exception) {
                job.log("[error] ${e.message}")
                if (job.cancelled) job.finish("cancelled")
                else {
                    job.state.update { it.copy(error = e.message) }
                    job.finish("error")
                }
            }
        }
        return job
    }

    fun cancel(id: String): Boolean {
        val j = jobs.firstOrNull { it.id == id } ?: return false
        if (j.state.value.status !in Job.ACTIVE) return false
        j.markCancelled()
        j.stopEngine()
        return true
    }

    private suspend fun run(ctx: Context, job: Job, profile: Profile, account: Account, version: String) {
        withContext(Dispatchers.IO) {
            try {
                val env = Paths.envOf(Paths.profileDir(ctx, profile.id))
                env.ensure()
                ProfilesRepository(ctx).touchLaunch(profile.id)
                job.stage("validate", 3, "checking profile and account")
                when (job.engine) {
                    "vengine" -> runVEngine(job, env, profile, account, version)
                    "bedrock" -> runBedrock(ctx, job, env, profile, account, version)
                    else -> runMinecraft(ctx, job, env, profile, account, version)
                }
            } catch (e: Exception) {
                if (job.cancelled) {
                    job.finish("cancelled")
                } else {
                    job.log("[error] ${e.message}")
                    job.state.update { it.copy(error = e.message) }
                    job.finish("error")
                }
            }
        }
    }

    private fun runVEngine(job: Job, env: EnvPaths, profile: Profile, account: Account, version: String) {
        job.log("[V Client] profile \"${profile.name}\" → isolated environment: ${env.root.absolutePath}")
        job.stage("environment", 10, "preparing isolated environment")
        File(env.game, "bin").mkdirs()
        File(env.game, "options.json").writeText(
            JSONObject().apply {
                put("fpsLimit", 240)
                put("renderDistance", 12)
                put("gamemode", "survival")
            }.toString(2)
        )
        Json.write(
            File(env.game, "engine-manifest.json"),
            JSONObject().apply {
                put("engine", "vengine")
                put("version", version)
                put("isolated", true)
                put("installedAt", System.currentTimeMillis())
            }
        )
        job.stage("install", 25, "engine installed into the environment")
        job.stage("session", 40, "writing isolated session")
        writeSession(env, account)
        job.stage("launch", 70, "starting isolated engine")
        val eng = com.venom.vclient.core.engine.VEngine(env, account.name, account.uuid, version)
        job.setEngine(eng)
        job.status("running")
        job.stage("running", 100, "running")
        emit()
        eng.start(
            onLog = { line -> job.log(line) },
            onExit = { code ->
                job.log("[V Client] engine exited (code=$code)")
                job.log("[V Client] duration: ${(System.currentTimeMillis() - job.state.value.startedAt) / 1000}s")
                job.finish(if (job.cancelled) "cancelled" else "stopped")
            }
        )
    }

    private fun writeSession(env: EnvPaths, account: Account) {
        Json.write(
            env.session,
            JSONObject().apply {
                put("name", account.name)
                put("uuid", account.uuid)
                put("type", account.type)
                put("accessToken", account.accessToken)
                put("createdAt", System.currentTimeMillis())
            }
        )
    }

    private fun runBedrock(ctx: Context, job: Job, env: EnvPaths, profile: Profile, account: Account, version: String) {
        job.log("[V Client] profile \"${profile.name}\" → isolated bedrock environment: ${env.root.absolutePath}")
        job.stage("environment", 15, "preparing isolated environment")
        val bd = Bedrock.bedrockDir(env)
        File(bd, "behavior_packs").mkdirs()
        File(bd, "resource_packs").mkdirs()
        File(bd, "addons").mkdirs()
        File(bd, "templates").mkdirs()
        File(bd, "config").mkdirs()

        val packs = Bedrock.listPacks(env)
        job.log("[bedrock] ${packs.count { it.enabled }} enabled pack(s) in the isolated environment")
        job.stage("packs", 40, "validating packs")
        for (p in packs) {
            val dir = File(Bedrock.packsDir(env, p.type), p.uuid)
            val ok = File(dir, "manifest.json").isFile
            job.log("[bedrock] ${p.name} (${p.type} v${p.version}) " + (if (ok) "✓" else "✗ missing manifest"))
            if (!ok) throw IllegalStateException("pack ${p.name} is incomplete — remove it and upload it again")
        }
        if (packs.isEmpty()) {
            job.log("[bedrock] no packs yet — upload .mcaddon/.mcpack files from the Packs screen")
        }

        job.stage("config", 65, "writing level config")
        Json.write(
            File(bd, "config/game.json"),
            JSONObject().apply {
                put("profile", profile.name)
                put("version", version)
                put("isolated", true)
                put(
                    "behaviorPacks",
                    JSONArray().apply {
                        packs.filter { it.type == "behavior" && it.enabled }.forEach { put(it.uuid) }
                    }
                )
                put(
                    "resourcePacks",
                    JSONArray().apply {
                        packs.filter { it.type == "resource" && it.enabled }.forEach { put(it.uuid) }
                    }
                )
                put("generatedAt", System.currentTimeMillis())
            }
        )

        job.stage("session", 80, "writing isolated session")
        writeSession(env, account)

        job.stage("ready", 100, "environment ready")
        val mc = Bedrock.isMinecraftInstalled(ctx)
        job.log(
            "[bedrock] Minecraft Bedrock " +
                (if (mc) "installed — deploy the packs from the Packs screen, then play" else "NOT installed — install it from the Play Store first")
        )
        if (!mc) job.state.update { it.copy(error = "MINECRAFT_NOT_INSTALLED") }
        job.finish("ready")
    }

    private fun runMinecraft(ctx: Context, job: Job, env: EnvPaths, profile: Profile, account: Account, version: String) {
        job.log("[V Client] profile \"${profile.name}\" → isolated environment: ${env.root.absolutePath}")

        job.stage("manifest", 10, "fetching version manifest")
        val manifest = Net.getJson(Mojang.MANIFEST_URL)
        val versions = manifest.getJSONArray("versions")
        var vid = version
        if (vid.isBlank() || vid == "latest") {
            vid = manifest.optJSONObject("latest")?.optString("release", "") ?: ""
            if (vid.isBlank()) vid = versions.getJSONObject(0).optString("id")
        }

        job.stage("version", 14, "downloading $vid version JSON")
        var vj: JSONObject? = null
        for (i in 0 until versions.length()) {
            val v = versions.getJSONObject(i)
            if (v.optString("id") == vid) {
                vj = Net.getJson(v.optString("url"))
                val verDir = File(env.versions, vid)
                verDir.mkdirs()
                File(verDir, "$vid.json").writeText(vj.toString(2))
                break
            }
        }
        val v = vj ?: throw IllegalStateException("version $vid not found in manifest")
        job.log("[V Client] version JSON ready: $vid (mainClass=${v.optString("mainClass")})")

        val libs = mutableListOf<JSONObject>()
        v.optJSONArray("libraries")?.let { la ->
            for (i in 0 until la.length()) {
                val l = la.getJSONObject(i)
                if (Mojang.clientSide(l)) libs.add(l)
            }
        }
        val tasks = mutableListOf<Pair<JSONObject, Mojang.LibSpec>>()
        for (l in libs) {
            Mojang.artifact(l)?.let { tasks.add(l to it) }
            Mojang.natives(l)?.let { tasks.add(l to it) }
        }
        job.stage("libraries", 18, "downloading ${tasks.size} libraries")
        var done = 0
        for ((l, spec) in tasks) {
            val dest = File(env.libraries, spec.rel)
            if (!Downloader.isSatisfied(dest, spec.size, spec.sha1)) {
                Downloader.download(spec.url, dest, spec.size, spec.sha1)
            }
            done++
            job.log("[lib] ${l.optString("name")}")
            if (done % 5 == 0) {
                job.stage(
                    "libraries",
                    18 + (27 * (done.toFloat() / tasks.size.coerceAtLeast(1))).toInt(),
                    "$done/${tasks.size} libraries"
                )
            }
        }

        job.stage("client", 46, "downloading client jar")
        val verDir = File(env.versions, vid)
        val clientJar = File(verDir, "$vid.jar")
        val cd = v.optJSONObject("downloads")?.optJSONObject("client")
            ?: throw IllegalStateException("this version has no client download")
        if (!Downloader.isSatisfied(clientJar, cd.optLong("size"), cd.optString("sha1").takeIf { it.isNotBlank() }, verify = true)) {
            Downloader.download(
                cd.optString("url"), clientJar, cd.optLong("size"),
                cd.optString("sha1").takeIf { it.isNotBlank() }
            ) { p ->
                job.stage("client", 46 + (14 * p).toInt(), "client jar ${(p * 100).toInt()}%")
            }
        }

        job.stage("assets", 62, "fetching asset index")
        val ai = v.optJSONObject("assetIndex") ?: throw IllegalStateException("no asset index")
        val aiFile = File(env.assetIndexes, "${ai.optString("id")}.json")
        val aiJson: JSONObject = Json.read(aiFile) ?: run {
            val txt = Net.getString(ai.optString("url"))
            aiFile.parentFile?.mkdirs()
            aiFile.writeText(txt)
            JSONObject(txt)
        }
        val objects = aiJson.optJSONObject("objects") ?: throw IllegalStateException("bad asset index")
        var a = 0
        val total = objects.length()
        for (key in objects.keys()) {
            val meta = objects.getJSONObject(key)
            val hash = meta.optString("hash", key)
            val dest = File(env.assetsObjects, hash.substring(0, 2) + "/" + hash)
            if (!dest.isFile) {
                dest.parentFile?.mkdirs()
                Downloader.download(Mojang.assetUrl(hash), dest, meta.optLong("size"))
            }
            a++
            if (a % 50 == 0) {
                job.stage("assets", 64 + (26 * (a.toFloat() / total.coerceAtLeast(1))).toInt(), "$a/$total assets")
            }
        }

        job.stage("natives", 91, "extracting natives")
        val nativesDir = File(verDir, "$vid-natives-linux")
        var nCount = 0
        for (l in libs) {
            val nat = Mojang.natives(l) ?: continue
            val jar = File(env.libraries, nat.rel)
            if (jar.isFile) {
                nativesDir.mkdirs()
                val exclude = l.optJSONObject("extract")?.optJSONArray("exclude")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: listOf("META-INF/")
                ZipExtractor.extract(jar, nativesDir, exclude)
                nCount++
            }
        }
        job.log("[natives] extracted from $nCount archive(s)")

        job.stage("session", 95, "writing isolated session")
        writeSession(env, account)

        val libFiles = libs.mapNotNull { l -> Mojang.artifact(l)?.rel?.let { r -> File(env.libraries, r) } }
        val args = JavaLaunch.build(
            version = v,
            gameDir = env.game,
            assetsRoot = env.assets,
            assetsIndexName = ai.optString("id"),
            nativesDir = nativesDir,
            libraryDir = env.libraries,
            libraries = libFiles,
            clientJar = clientJar,
            memMax = profile.memoryMax,
            memMin = profile.memoryMin,
            account = account
        )
        val cmdLine = "java " + args.joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it }
        File(env.reports, "last-launch.txt").writeText(cmdLine + "\n")
        val sh = File(env.reports, "launch.sh")
        sh.writeText("#!/data/data/com.termux/files/usr/bin/bash\n" + cmdLine + "\n")
        sh.setExecutable(true, true)
        job.log("[V Client] full launch command saved → reports/last-launch.txt")

        if (TermuxBridge.isInstalled(ctx)) {
            job.stage("launch", 100, "sending launch to Termux")
            job.log("[V Client] Termux found — starting the game in the isolated environment")
            TermuxBridge.runCommand(ctx, cmdLine)
            job.status("running")
            emit()
        } else {
            job.state.update { it.copy(error = "TERMUX_NOT_FOUND") }
            job.log("[V Client] Termux is not installed. Minecraft Java runs inside Termux on Android — install it (F-Droid) and launch again.")
            job.finish("dryrun")
        }
    }
}
