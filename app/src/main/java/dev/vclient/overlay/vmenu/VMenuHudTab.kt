package dev.vclient.overlay.vmenu

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.vclient.core.VClientCore
import dev.vclient.core.config.ProfileManager
import dev.vclient.core.config.WaypointDto
import dev.vclient.core.game.InputTracker
import dev.vclient.core.game.VKey
import dev.vclient.overlay.accessibility.VAccessibilityService
import dev.vclient.ui.widgets.NumberField

/** HUD tab: layout editor entry, element visibility, waypoints, touch mapping. */
@Composable
fun HudTab(onOpenEditor: () -> Unit) {
    val core = VClientCore.core
    var showWaypointForm by remember { mutableStateOf(false) }
    val profile by core.profiles.activeData.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { SectionTitle("Layout") }
        item {
            Button(
                onClick = onOpenEditor,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.OpenWith, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open HUD Editor")
            }
        }

        item { SectionTitle("Elements") }
        items(core.modules.hudModules, key = { it.hud.id }) { module ->
            val enabled by module.enabledFlow.collectAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(module.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${module.hud.anchor.id} · ×%.2f".format(module.hud.scale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { module.setEnabled(it) })
            }
        }

        item { SectionTitle("Waypoints") }
        items(profile.waypoints, key = { it.id }) { waypoint ->
            WaypointRow(waypoint)
        }
        item {
            if (showWaypointForm) {
                WaypointForm(onDone = { showWaypointForm = false })
            } else {
                OutlinedButton(
                    onClick = { showWaypointForm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add waypoint")
                }
            }
        }

        item { SectionTitle("Touch mapping (Android 14+)") }
        item { TouchMappingSection() }
    }
}

@Composable
private fun WaypointRow(waypoint: WaypointDto) {
    val core = VClientCore.core
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(waypoint.colorArgb)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(waypoint.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "%.0f, %.0f, %.0f".format(waypoint.x, waypoint.y, waypoint.z),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {
            core.profiles.upsertWaypoint(waypoint.copy(visible = !waypoint.visible))
        }) {
            Icon(
                if (waypoint.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                contentDescription = "Toggle visibility",
                tint = if (waypoint.visible) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { core.profiles.removeWaypoint(waypoint.id) }) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete waypoint", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private val WaypointColors = listOf(
    0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt(), 0xFF34D399.toInt(),
    0xFFFBBF24.toInt(), 0xFFF87171.toInt(), 0xFF60A5FA.toInt(),
)

@Composable
private fun WaypointForm(onDone: () -> Unit) {
    val core = VClientCore.core
    var name by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("64") }
    var z by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(WaypointColors.first()) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("X", x, { x = it }, Modifier.weight(1f))
                NumberField("Y", y, { y = it }, Modifier.weight(1f))
                NumberField("Z", z, { z = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaypointColors.forEach { c ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                width = if (c == color) 3.dp else 1.dp,
                                color = if (c == color) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { color = c },
                    )
                }
            }
            Row {
                Button(
                    onClick = {
                        val xd = x.toDoubleOrNull() ?: return@Button
                        val yd = y.toDoubleOrNull() ?: 64.0
                        val zd = z.toDoubleOrNull() ?: return@Button
                        core.profiles.upsertWaypoint(
                            WaypointDto(
                                id = ProfileManager.newWaypointId(),
                                name = name.ifBlank { "Waypoint" },
                                x = xd, y = yd, z = zd,
                                dimension = core.session.state.value.dimension.name,
                                colorArgb = color,
                            )
                        )
                        onDone()
                    },
                    enabled = x.toDoubleOrNull() != null && z.toDoubleOrNull() != null,
                ) { Text("Save waypoint") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDone) { Text("Cancel") }
            }
        }
    }
}

/**
 * Maps calibrated on-screen rectangles to virtual keys. The user taps
 * "Record", then presses the game's own on-screen control; the accessibility
 * motion-event feed captures the rectangle and Keystrokes/CPS go live.
 */
@Composable
private fun TouchMappingSection() {
    val core = VClientCore.core
    val context = LocalContext.current
    val regions by InputTracker.regions.collectAsState()

    if (Build.VERSION.SDK_INT < 34 || !VAccessibilityService.isConnected) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Real keystroke & CPS data needs the V Client accessibility service on Android 14+.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Open accessibility settings") }
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Region mapping", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Press Record, then touch the game control on screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = InputTracker.mappingEnabled,
                onCheckedChange = { InputTracker.mappingEnabled = it },
            )
        }
        VKey.entries.forEach { key ->
            val region = regions.firstOrNull { it.key == key }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(key.display, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    region?.toString() ?: "not mapped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { InputTracker.armRecording(key) }) { Text("Record") }
            }
        }
        if (InputTracker.isRecordingArmed) {
            Text(
                "Now touch the control on screen to capture it…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
