package dev.vclient.overlay.vmenu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vclient.core.VClientCore

/**
 * V Menu — the modern in-game GUI. Bottom-sheet overlay with four sections:
 * Modules, HUD (layout/waypoints/touch mapping), Profiles and Settings.
 * Every change is applied live and auto-saved into the active profile.
 */
@Composable
fun VMenuRoot(onClose: () -> Unit, onOpenEditor: () -> Unit) {
    val core = VClientCore.core
    var tab by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("V Menu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${core.profiles.activeName.value} · ${core.modules.enabledCount} modules active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close V Menu")
                }
            }

            if (core.safeMode && !core.settings.safeModeAcknowledged) {
                SafeModeBanner { core.settings.safeModeAcknowledged = true }
            }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ModulesTab()
                    1 -> HudTab(onOpenEditor)
                    2 -> ProfilesTab()
                    else -> VMenuSettingsTab()
                }
            }

            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.GridView, "Modules") }, label = { Text("Modules") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Edit, "HUD") }, label = { Text("HUD") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.Folder, "Profiles") }, label = { Text("Profiles") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Rounded.Settings, "Settings") }, label = { Text("Settings") })
            }
        }
    }
}

@Composable
private fun SafeModeBanner(onAcknowledge: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    "  Safe mode active",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "V Client crashed repeatedly last session, so every module was disabled. " +
                    "Re-enable modules one by one to find the cause — details are in the logs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAcknowledge) { Text("Got it") }
        }
    }
}

/** Section heading shared by all tabs. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
    )
}
