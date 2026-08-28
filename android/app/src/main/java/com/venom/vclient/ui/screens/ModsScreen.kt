package com.venom.vclient.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UploadFile
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
import android.provider.OpenableColumns

@Composable
fun ModsScreen(repos: Repos) {
    val ctx = LocalContext.current
    val lang = repos.settings.language
    val t: (String) -> String = { S.t(lang, it) }
    var tick by remember { mutableIntStateOf(0) }
    val profiles = remember(tick) { repos.profiles.list() }
    var profileId by rememberSaveable { mutableStateOf("") }
    val profile = profiles.firstOrNull { it.id == profileId } ?: profiles.firstOrNull()
    val modsDir = if (profile != null) File(Paths.profileDir(ctx, profile.id), "mods") else null
    val mods = remember(tick, profile?.id) {
        if (modsDir == null) emptyList<File>()
        else modsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val dir = modsDir
        if (profile == null || dir == null || uris.isEmpty()) return@rememberLauncherForActivityResult
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val raw = queryName(ctx, uri) ?: "mod.jar"
                    val safe = File(raw).name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "mod.jar" }
                    val dest = File(dir, safe)
                    try {
                        ctx.contentResolver.openInputStream(uri)?.use { ins ->
                            dest.outputStream().use { ins.copyTo(it) }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            tick++
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column {
                Text(t("mods.title"), fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(t("mods.note"), color = Color(0xFFA1A1AA), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
                VGhostButton(t("mods.upload")) { launcher.launch(arrayOf("*/*")) }
            }
        }
        if (mods.isEmpty()) {
            item { VEmpty(t("mods.empty"), t("mods.emptyDesc")) }
        }
        items(mods) { f ->
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
                        null,
                        tint = Color(0xFF71717A),
                        modifier = Modifier
                            .padding(start = 10.dp)
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

private fun queryName(ctx: android.content.Context, uri: android.net.Uri): String? {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
    }
    return null
}
