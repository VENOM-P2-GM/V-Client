package com.venom.vclient.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venom.vclient.core.Job
import com.venom.vclient.core.Mojang
import com.venom.vclient.core.Net
import com.venom.vclient.core.Pipeline
import com.venom.vclient.ui.Picker
import com.venom.vclient.ui.Repos
import com.venom.vclient.ui.S
import com.venom.vclient.ui.VButton
import com.venom.vclient.ui.VCard
import com.venom.vclient.ui.VChip
import com.venom.vclient.ui.VEmpty
import com.venom.vclient.ui.VField
import com.venom.vclient.ui.VLog
import com.venom.vclient.ui.VProgress
import com.venom.vclient.ui.VGhostButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(repos: Repos) {
    val lang = repos.settings.language
    val t: (String) -> String = { S.t(lang, it) }
    var profiles by remember { mutableStateOf(repos.profiles.list()) }
    var accounts by remember { mutableStateOf(repos.accounts.list()) }
    val jobs by Pipeline.states.collectAsState()
    var profileId by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableStateOf("") }
    var memMax by rememberSaveable { mutableIntStateOf(4096) }
    var launchErr by remember { mutableStateOf("") }
    var versions by remember { mutableStateOf(listOf("1.1.0", "1.0.0")) }

    val profile = profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull()
    val account = accounts.firstOrNull { it.id == accountId } ?: accounts.firstOrNull()
    val engine = profile?.engine ?: "vengine"

    LaunchedEffect(engine, profile?.id) {
        memMax = profile?.memoryMax ?: repos.settings.defaultMemoryMax
        if (engine == "minecraft") {
            versions = withContext(Dispatchers.IO) {
                try {
                    val m = Net.getJson(Mojang.MANIFEST_URL)
                    val arr = m.getJSONArray("versions")
                    (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
                } catch (e: Exception) {
                    listOf("latest")
                }
            }
        } else {
            versions = listOf("1.1.0", "1.0.0")
        }
    }

    val effectiveVersion = versions.firstOrNull { it == version } ?: versions.firstOrNull() ?: "latest"
    val activeJob = jobs.firstOrNull { it.status in Job.ACTIVE }
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF12121F),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("V", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        }
                        Column {
                            Text("V Client", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text(t("app.tagline"), color = Color(0xFFA1A1AA), fontSize = 12.sp)
                        }
                    }
                    Text(
                        profile?.name ?: t("home.noProfileTitle"),
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        profile?.description ?: t("home.envDesc"),
                        color = Color(0xFFA1A1AA),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (profile != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                            VChip(if (profile.engine == "minecraft") t("engine.mine") else t("engine.demo"))
                            VChip(
                                t("home.lastLaunch") + ": " +
                                    (if (profile.lastLaunch != null) dateFormat.format(Date(profile.lastLaunch)) else t("home.never")),
                                color = Color(0xFFA1A1AA)
                            )
                        }
                    }
                }
            }
        }

        item {
            val j = activeJob
            if (j != null) {
                VCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (j.status == "running") t("home.runningTitle") else t("home.launching"),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            VGhostButton(if (j.status == "running") t("home.stop") else t("home.cancel")) {
                                Pipeline.cancel(j.id)
                            }
                        }
                        VProgress(j.progress, Modifier.padding(top = 12.dp))
                        Text(
                            j.stageMsg.ifBlank { t("stage." + j.stage) },
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        val order = if (j.engine == "minecraft") {
                            listOf("validate", "environment", "manifest", "version", "libraries", "client", "assets", "natives", "session", "launch", "running")
                        } else {
                            listOf("validate", "environment", "install", "session", "launch", "running")
                        }
                        Text(
                            order.joinToString("  ←  ") { s ->
                                val done = order.indexOf(j.stage) > order.indexOf(s)
                                if (done) "✓ " else ""
                            }.let { full ->
                                full.replace(t("stage." + j.stage), "▸ " + t("stage." + j.stage) + " ▸")
                            },
                            color = Color(0xFF71717A),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        VLog(j.logTail, Modifier.padding(top = 12.dp))
                        when (j.status) {
                            "dryrun" -> Text(
                                t("home.dryrunTitle") + "\n" + t("home.dryrunDesc"),
                                color = Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            "error" -> Text(
                                j.error ?: t("home.failed"),
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            "stopped" -> Text(
                                t("home.stopped"),
                                color = Color(0xFFA1A1AA),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (activeJob == null) {
            item {
                VCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VField(t("home.profile")) {
                            Picker(
                                profiles.map { it.name },
                                profile?.name ?: "",
                                { sel -> profileId = profiles.firstOrNull { it.name == sel }?.id ?: "" },
                                Modifier.fillMaxWidth()
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VField(t("home.account")) {
                                Picker(
                                    accounts.map { it.name },
                                    account?.name ?: "",
                                    { sel -> accountId = accounts.firstOrNull { it.name == sel }?.id ?: "" },
                                    Modifier.weight(1f)
                                )
                            }
                            VField(t("home.version")) {
                                Picker(versions, effectiveVersion, { version = it }, Modifier.weight(1f))
                            }
                        }
                        VField(t("home.memory") + " — " + (memMax / 1024) + " GB") {
                            Slider(memMax.toFloat(), 16384f, onValueChange = { memMax = it.toInt() })
                        }
                        if (launchErr.isNotBlank()) {
                            Text(launchErr, color = Color(0xFFF87171), fontSize = 12.sp)
                        }
                        VButton(
                            t("home.launch"),
                            {
                                val p = profile
                                val a = account
                                if (p == null || a == null) return@VButton
                                launchErr = ""
                                try {
                                    Pipeline.start(LocalContext.current, p, a, effectiveVersion)
                                } catch (e: Exception) {
                                    launchErr = e.message ?: "error"
                                }
                            },
                            Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        if (profile != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val env = repos.profiles.envOf(profile.id)
                    envInfo(t("home.envProcess"), if (activeJob?.status == "running") "isolated ✓" else "—", Modifier.weight(1f))
                    envInfo(t("home.envData"), env.root.name, Modifier.weight(1f))
                    envInfo(t("home.envSession"), "auth/session.json", Modifier.weight(1f))
                }
            }
        }

        if (profile == null) {
            item {
                VCard {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(t("home.noProfileTitle"), fontWeight = FontWeight.Bold)
                        Text(t("home.noProfileDesc"), color = Color(0xFFA1A1AA), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun envInfo(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0x0AFFFFFF),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color(0xFFA1A1AA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
