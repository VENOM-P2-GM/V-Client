package dev.vclient.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vclient.core.VClientCore
import dev.vclient.core.config.ProfileSync

/**
 * Profile manager: activate (applies instantly), create, delete, and export /
 * import profiles as JSON files through the system file picker.
 */
@Composable
fun ProfilesScreen() {
    val core = VClientCore.core
    val profiles by core.profiles.profileList.collectAsState()
    val active by core.profiles.activeName.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val target = exportTarget
        if (uri != null && target != null) {
            val ok = core.profiles.export(target, uri)
            importMessage = if (ok) "Exported '$target'." else "Export failed — see logs."
        }
        exportTarget = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = core.profiles.import(uri)
            importMessage = if (name != null) "Imported profile '$name'." else "Import failed — see logs."
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Profiles",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Text(
            "Each profile stores module states, settings, HUD layout and waypoints.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        importMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(profiles, key = { it.name }) { meta ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = meta.name == active,
                        onClick = {
                            core.profiles.activate(meta.name)?.let { data ->
                                ProfileSync.apply(data, core.modules, core.logger)
                                core.settings.activeProfileName = meta.name
                            }
                        },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            meta.name + if (meta.name == active) "  ·  active" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(meta.modifiedAtMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { exportTarget = meta.name; exportLauncher.launch("${meta.name}.json") }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Export")
                    }
                    if (meta.name != active) {
                        IconButton(onClick = { core.profiles.delete(meta.name) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Button(onClick = { showCreateDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New profile")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                        Text("Import")
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New profile") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && core.profiles.create(name, copyFrom = active)) {
                            showCreateDialog = false
                        }
                    },
                ) { Text("Create (copies active)") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
        )
    }
}
