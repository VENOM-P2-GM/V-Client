package com.venom.vclient.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venom.vclient.core.Bedrock
import com.venom.vclient.core.Paths
import com.venom.vclient.ui.Picker
import com.venom.vclient.ui.Repos
import com.venom.vclient.ui.S
import com.venom.vclient.ui.VCard
import com.venom.vclient.ui.VChip
import com.venom.vclient.ui.VEmpty
import com.venom.vclient.ui.VGhostButton
import com.venom.vclient.ui.humanSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ModsScreen(repos: Repos) {
    val ctx = LocalContext.current
    val lang = repos.settings.language
    val t: (String) -> String = { S.t(lang, it) }
    var tick by remember { mutableIntStateOf(0) }
    val profiles = remember(tick) { repos.profiles.list() }
    var profileId by rememberSaveable { mutableStateOf("") }
    val profile = profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull()
    val isBedrock = profile?.engine == "bedrock"
    var lastMsg by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (profile == null || uris.isEmpty()) return@rememberLauncherForActivityResult
        val p = profile
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                val env = Paths.envOf(Paths.profileDir(ctx, p.id))
                val cacheDir = File(ctx.cacheDir, "uploads").apply { mkdirs() }
                for (uri in uris) {
                    val raw = queryName(ctx, uri) ?: "upload"
                    val safe = File(raw).name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "upload" }
                    val tmp = File(cacheDir, safe)
                    try {
                        ctx.contentResolver.openInputStream(uri)?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                        if (isBedrock) {
                            Bedrock.installPacks(env, tmp) { msg -> runOnUiThread { lastMsg = msg } }
                        } else {
                            val modsDir = File(env.mods).apply { mkdirs() }
                            tmp.copyTo(File(modsDir, safe), overwrite = true)
                        }
                        tmp.delete()
                    } catch (e: Exception) {
                        lastMsg = "⚠ $safe: ${e.message}"
                    }
                }
            }
            tick++
        }
    }

    // vengine mods / bedrock packs
    val env = if (profile != null) Paths.envOf(Paths.profileDir(ctx, profile.id)) else null
    val vMods: List<File> = remember(tick, profile?.id) {
        if (!isBedrock && env != null) env.mods.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
        else emptyList()
    }
    val bPacks: List<Bedrock.Pack> = remember(tick, profile?.id) {
        if (isBedrock && env != null) Bedrock.listPacks(env) else emptyList()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column {
                Text(if (isBedrock) t("mods.packs") else t("mods.title"), fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(
                    if (isBedrock) t("mods.packsNote") else t("mods.note"),
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Picker(
                    profiles.map { it.name },
                    profile?.name ?: "",
                    { sel -> profileId = profiles.firstOrNull { it.name == sel }?.id ?: "" },
                    Modifier.weight(1f)
                )
                VGhostButton(if (isBedrock) t("mods.uploadPack") else t("mods.upload")) {
                    launcher.launch(arrayOf("*/*"))
                }
            }
        }
        if (lastMsg.isNotBlank()) {
            item { Text(lastMsg, color = Color(0xFFA3E635), fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }

        if (isBedrock) {
            if (bPacks.isEmpty()) {
                item { VEmpty(t("mods.empty"), t("mods.emptyDesc")) }
            }
            items(bPacks) { pk ->
                VCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pk.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VChip(t("mods." + pk.type), Modifier.padding(end = 6.dp))
                                Text("v" + pk.version, color = Color(0xFF71717A), fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = pk.enabled,
                            onCheckedChange = {
                                if (env != null) Bedrock.setEnabled(env, pk.uuid, it)
                                tick++
                            }
                        )
                        Icon(
                            Icons.Outlined.Send,
                            t("mods.deploy"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp)
                                .clickable {
                                    val e = env ?: return@clickable
                                    val uri = Bedrock.deployUri(ctx, e, pk)
                                    if (uri == null) lastMsg = "⚠ no raw file for this pack"
                                    else if (!Bedrock.openWithMinecraft(ctx, uri)) lastMsg = "⚠ Minecraft not available"
                                }
                        )
                        Icon(
                            Icons.Outlined.Delete,
                            t("mods.remove"),
                            tint = Color(0xFF71717A),
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .size(20.dp)
                                .clickable {
                                    val e = env ?: return@clickable
                                    Bedrock.removePack(e, pk.uuid)
                                    tick++
                                }
                        )
                    }
                }
            }
        } else {
            if (vMods.isEmpty()) {
                item { VEmpty(t("mods.empty"), t("mods.emptyDesc")) }
            }
            items(vMods) { f ->
                val disabled = f.name.endsWith(".disabled")
                VCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name.removeSuffix(".disabled"), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                humanSize(f.length()) + " · " + (if (disabled) t("mods.disabled") else t("mods.enabled")),
                                color = Color(0xFF71717A),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = !disabled,
                            onCheckedChange = {
                                CoroutineScope(Dispatchers.Main).launch {
                                    withContext(Dispatchers.IO) {
                                        val to = File(f.parentFile, if (disabled) f.name.removeSuffix(".disabled") else f.name + ".disabled")
                                        f.renameTo(to)
                                    }
                                    tick++
                                }
                            }
                        )
                        Icon(
                            Icons.Outlined.Delete,
                            t("mods.remove"),
                            tint = Color(0xFF71717A),
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .size(20.dp)
                                .clickable {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        withContext(Dispatchers.IO) { f.delete() }
                                        tick++
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

private fun runOnUiThread(block: () -> Unit) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    handler.post(block)
}

private fun queryName(ctx: android.content.Context, uri: android.net.Uri): String? {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
    }
    return null
}
