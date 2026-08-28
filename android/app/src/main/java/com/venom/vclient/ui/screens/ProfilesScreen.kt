package com.venom.vclient.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venom.vclient.core.Job
import com.venom.vclient.core.Pipeline
import com.venom.vclient.core.Profile
import com.venom.vclient.core.Paths
import com.venom.vclient.ui.Repos
import com.venom.vclient.ui.S
import com.venom.vclient.ui.VButton
import com.venom.vclient.ui.VCard
import com.venom.vclient.ui.VChip
import com.venom.vclient.ui.VEmpty
import com.venom.vclient.ui.VGhostButton
import com.venom.vclient.ui.humanSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.walkTopDown

private fun File.du(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

@Composable
private fun RowScope.EnginePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0x0AFFFFFF))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
fun ProfilesScreen(repos: Repos) {
    val lang = repos.settings.language
    val t: (String) -> String = { S.t(lang, it) }
    var tick by remember { mutableIntStateOf(0) }
    val refresh = { tick++ }
    val profiles by remember(tick) { mutableStateOf(repos.profiles.list()) }
    val jobs by Pipeline.states.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("vengine") }
    var version by remember { mutableStateOf("1.1.0") }
    var desc by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("profiles.title"), fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                VGhostButton(t("profiles.new")) {
                    name = ""; engine = "vengine"; version = "1.1.0"; desc = ""; err = ""
                    showNew = true
                }
            }
        }
        if (profiles.isEmpty()) {
            item { VEmpty(t("profiles.empty"), t("profiles.emptyDesc")) }
        }
        items(profiles) { p ->
            val running = jobs.any { it.profileId == p.id && it.status in Job.ACTIVE }
            VCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(
                                (if (p.engine == "minecraft") t("engine.mine") else t("engine.demo")) + " · " + p.version,
                                color = Color(0xFF71717A),
                                fontSize = 11.sp
                            )
                        }
                        if (running) VChip("●", color = Color(0xFFA3E635))
                        VGhostButton(t("profiles.envTitle")) {
                            expanded = if (expanded == p.id) null else p.id
                        }
                    }
                    if (p.description.isNotBlank()) {
                        Text(p.description, color = Color(0xFFA1A1AA), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        VChip(t("profiles.launches") + ": " + p.launchCount, color = Color(0xFFA1A1AA))
                        VChip(
                            t("profiles.last") + ": " +
                                (if (p.lastLaunch != null) java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(p.lastLaunch)) else t("home.never")),
                            color = Color(0xFFA1A1AA)
                        )
                    }
                    if (expanded == p.id) EnvTree(p.id)
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        VButton(t("common.delete"), { toDelete = p }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text(t("profiles.new"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, onValueChange = { name = it }, label = { Text(t("profiles.name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EnginePill(t("profiles.vengineName"), engine == "vengine") {
                                engine = "vengine"; version = "1.1.0"
                            }
                            EnginePill(t("profiles.minecraftName"), engine == "minecraft") {
                                engine = "minecraft"; version = "latest"
                            }
                        }
                        Row {
                            EnginePill(t("profiles.bedrockName"), engine == "bedrock") {
                                engine = "bedrock"; version = "latest"
                            }
                        }
                    }
                    OutlinedTextField(version, onValueChange = { version = it }, label = { Text(t("profiles.version")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(desc, onValueChange = { desc = it }, label = { Text(t("profiles.desc")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (err.isNotBlank()) Text(err, color = Color(0xFFF87171), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    err = ""
                    try {
                        val v = if (engine == "minecraft" && (version.isBlank() || version == "latest")) "latest" else version
                        repos.profiles.create(name.ifBlank { "Default" }, engine, v.ifBlank { "1.1.0" }, desc, repos.settings.defaultMemoryMax, repos.settings.defaultMemoryMin)
                        showNew = false
                        refresh()
                    } catch (e: Exception) {
                        err = e.message ?: "error"
                    }
                }) {
                    Text(t("profiles.create"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNew = false }) { Text(t("common.cancel")) }
            }
        )
    }

    val delTarget = toDelete
    if (delTarget != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(t("common.delete"), fontWeight = FontWeight.Bold) },
            text = { Text(t("profiles.deleteConfirm"), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    repos.profiles.delete(delTarget.id)
                    toDelete = null
                    refresh()
                }) {
                    Text(t("common.confirm"), color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(t("common.cancel")) }
            }
        )
    }
}

@Composable
private fun EnvTree(profileId: String) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<Pair<Long, Map<String, Long>>?>(null) }
    LaunchedEffect(profileId) {
        info = withContext(Dispatchers.IO) {
            val root = Paths.profileDir(ctx, profileId)
            val folders = listOf("game", "saves", "mods", "auth", "logs", "reports")
            val m = folders.associateWith { File(root, it).du() }
            root.du() to m
        }
    }
    Column(Modifier.padding(top = 10.dp)) {
        Text("profiles/$profileId", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF71717A))
        info?.let { (total, m) ->
            Text("— " + humanSize(total), fontSize = 11.sp, color = Color(0xFFA3E635), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            m.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("$k/", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFA1A1AA), modifier = Modifier.weight(1f))
                    Text(humanSize(v), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF71717A))
                }
            }
        }
    }
}
