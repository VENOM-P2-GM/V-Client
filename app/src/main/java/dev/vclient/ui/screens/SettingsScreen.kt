package dev.vclient.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.vclient.core.VClientCore
import dev.vclient.core.runtime.NativeBridge
import dev.vclient.overlay.accessibility.VAccessibilityService
import dev.vclient.overlay.vmenu.ManualPositionCard
import dev.vclient.overlay.vmenu.SectionTitle
import dev.vclient.overlay.vmenu.SwitchRow
import dev.vclient.ui.Routes

/** Launcher settings: overlay behaviour, game data, diagnostics, about. */
@Composable
fun SettingsScreen(nav: NavController) {
    val core = VClientCore.core

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

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
            SwitchRow(
                title = "Accessibility service",
                subtitle = if (VAccessibilityService.isConnected) "Connected."
                else "Off — Zoom, focus detection and touch mapping need it.",
                checked = VAccessibilityService.isConnected,
            ) { }
        }

        item { SectionTitle("Game data") }
        item {
            ManualPositionCard()
        }

        item { SectionTitle("Data & diagnostics") }
        item {
            Text(
                "All V Client data is isolated inside its own sandbox. Nothing is ever written " +
                    "to Minecraft's files, and the game's APK is never modified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(onClick = { nav.navigate(Routes.LOGS) }, modifier = Modifier.fillMaxWidth()) {
                Text("View logs & crash reports")
            }
        }
        item {
            OutlinedButton(onClick = {
                core.paths.wipeCache()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear cache (${core.paths.cache.listFiles()?.size ?: 0} files)")
            }
        }
        item {
            OutlinedButton(onClick = {
                core.signatures.forgetTrust()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Forget trusted Minecraft signature")
            }
        }

        item { SectionTitle("About") }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("V Client 1.0.0", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Native runtime: ${NativeBridge.version()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "V Bridge: ${NativeBridge.bridgeStatus()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(0.dp))
                Text(
                    "V Client is an independent utility overlay. It is not affiliated with, " +
                        "endorsed by, or connected to Mojang or Microsoft. Use it responsibly and " +
                        "in line with the rules of the servers you play on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
