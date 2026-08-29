package dev.vclient.overlay.vmenu

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.vclient.core.VClientCore
import dev.vclient.core.config.ProfileSync
import dev.vclient.core.game.DataSourceKind
import dev.vclient.core.game.GameDimension
import dev.vclient.core.runtime.NativeBridge
import dev.vclient.ui.widgets.NumberField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Profiles tab: switch, create, delete (rename/export/import live in the launcher). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesTab() {
    val core = VClientCore.core
    val profiles by core.profiles.profileList.collectAsState()
    val active by core.profiles.activeName.collectAsState()
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            item { SectionTitle("Switch profile") }
            items(profiles, key = { it.name }) { meta ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(vertical = 2.dp),
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
                        Text(meta.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(meta.modifiedAtMs)) +
                                if (meta.gameVersion.isNotBlank()) " · MC ${meta.gameVersion}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (meta.name != active) {
                        IconButton(onClick = { core.profiles.delete(meta.name) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { SectionTitle("New profile") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Profile name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            if (core.profiles.create(newName, copyFrom = active)) newName = ""
                        },
                    ) { Text("Create") }
                }
            }
            item {
                Text(
                    "New profiles start as a copy of the active one. Export, import and renaming are available in the launcher app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** V Menu settings: data source, manual position, overlay behaviour, diagnostics. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VMenuSettingsTab() {
    val core = VClientCore.core
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { SectionTitle("Game data source") }
        item {
            var selectedKind by remember { mutableStateOf(core.session.sourceKind) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataSourceKind.entries.forEach { kind ->
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = {
                            core.settings.dataSourceKind = kind.id
                            selectedKind = core.session.applyDataSource(kind.id)
                            core.logger.i("VMenu", "Data source -> ${selectedKind.id}")
                        },
                        label = { Text(kind.display) },
                    )
                }
            }
            Text(
                DataSourceKind.byId(core.settings.dataSourceKind).detail +
                    if (DataSourceKind.byId(core.settings.dataSourceKind) == DataSourceKind.BRIDGE)
                        " Not available in this build — falls back to Manual." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item { SectionTitle("Manual position") }
        item { ManualPositionCard() }

        item { SectionTitle("Overlay") }
        item {
            SwitchRow(
                title = "Hide HUD outside Minecraft",
                subtitle = "Needs the accessibility service for focus detection.",
                checked = core.settings.hideOutsideGame,
            ) { core.settings.hideOutsideGame = it }
        }
        item {
            SwitchRow(
                title = "Blurred V Menu backdrop",
                subtitle = "Android 12+; falls back to a dim backdrop.",
                checked = core.settings.vmenuBlurBackdrop,
            ) { core.settings.vmenuBlurBackdrop = it }
        }
        item {
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) {
                Icon(Icons.Rounded.Accessibility, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Open accessibility settings")
            }
        }

        item { SectionTitle("Diagnostics") }
        item { InfoRow("Native runtime", NativeBridge.version()) }
        item { InfoRow("V Bridge", NativeBridge.bridgeStatus()) }
        item { InfoRow("Modules registered", "${core.modules.all.size} (${core.modules.enabledCount} enabled)") }
        item { InfoRow("Module errors", core.modules.errors.value.size.toString()) }
        item { InfoRow("Isolated data root", core.paths.root.absolutePath) }
    }
}

@Composable
fun ManualPositionCard() {
    val core = VClientCore.core
    var x by remember { mutableStateOf(core.settings.manualX.toString()) }
    var y by remember { mutableStateOf(core.settings.manualY.toString()) }
    var z by remember { mutableStateOf(core.settings.manualZ.toString()) }
    var yaw by remember { mutableStateOf(core.settings.manualYaw) }
    var dimension by remember { mutableStateOf(GameDimension.byId(core.settings.manualDimension)) }

    fun persist() {
        core.settings.manualX = x.toFloatOrNull() ?: core.settings.manualX
        core.settings.manualY = y.toFloatOrNull() ?: core.settings.manualY
        core.settings.manualZ = z.toFloatOrNull() ?: core.settings.manualZ
        core.settings.manualYaw = yaw
        core.settings.manualDimension = dimension.name
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Used by Coordinates, Waypoints and other position modules while the bridge is unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("X", x, { x = it; persist() }, Modifier.weight(1f))
                NumberField("Y", y, { y = it; persist() }, Modifier.weight(1f))
                NumberField("Z", z, { z = it; persist() }, Modifier.weight(1f))
            }
            Text("Yaw: ${yaw.toInt()}°", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = yaw,
                onValueChange = { yaw = it; persist() },
                valueRange = 0f..360f,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameDimension.entries.forEach { dim ->
                    FilterChip(
                        selected = dimension == dim,
                        onClick = { dimension = dim; persist() },
                        label = { Text(dim.display) },
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
