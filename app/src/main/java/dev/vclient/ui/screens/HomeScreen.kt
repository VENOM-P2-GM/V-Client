package dev.vclient.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import dev.vclient.core.VClientCore
import dev.vclient.core.signature.SignatureState
import dev.vclient.core.version.CompatStatus
import dev.vclient.overlay.OverlayService
import dev.vclient.overlay.accessibility.VAccessibilityService
import dev.vclient.ui.Routes
import dev.vclient.ui.components.StatusCard
import dev.vclient.ui.components.StatusPill
import dev.vclient.ui.components.StatusTone

/**
 * Home — the launch flow:
 *  1. Detect Minecraft, resolve version compatibility.
 *  2. Verify (and on first run, trust) the game's APK signature.
 *  3. Check overlay permission.
 *  4. Start the overlay session and launch the stock game.
 */
@Composable
fun HomeScreen(nav: NavController) {
    val core = VClientCore.core
    val context = LocalContext.current
    val signature by core.signatures.status.collectAsState()
    val overlayRunning = OverlayService.isRunning

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    // Refresh permission states whenever the user returns from system settings.
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        overlayGranted = Settings.canDrawOverlays(context)
        notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsGranted = it }

    val gameVersion = core.launcher.installedVersionName()
    val installed = gameVersion != null
    val compat = core.versions.infoFor(gameVersion)
    val sigState = signature.state
    val canLaunch = installed && overlayGranted &&
        (sigState == SignatureState.VERIFIED || sigState == SignatureState.UNTRUSTED)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Branding
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("V Client", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Bedrock utility client · isolated · no APK modification",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (core.safeMode && !core.settings.safeModeAcknowledged) {
            StatusCard(
                icon = Icons.Rounded.WarningAmber,
                title = "Safe mode",
                subtitle = "V Client crashed repeatedly last session — all modules are disabled.",
                tone = StatusTone.NEGATIVE,
                actionLabel = "Got it",
                onAction = { core.settings.safeModeAcknowledged = true },
            )
        }

        // Minecraft detection + compatibility + signature
        StatusCard(
            icon = when {
                !installed -> Icons.Rounded.ErrorOutline
                sigState == SignatureState.VERIFIED -> Icons.Rounded.Verified
                sigState == SignatureState.MISMATCH -> Icons.Rounded.WarningAmber
                else -> Icons.Rounded.Security
            },
            title = if (installed) "Minecraft $gameVersion" else "Minecraft not found",
            subtitle = when {
                !installed -> "Install Minecraft Bedrock from Google Play, then return here."
                sigState == SignatureState.VERIFIED -> signature.message
                sigState == SignatureState.UNTRUSTED -> "Review the signature below, then trust it once."
                sigState == SignatureState.MISMATCH -> signature.message
                else -> signature.message
            },
            tone = when {
                !installed -> StatusTone.NEGATIVE
                sigState == SignatureState.VERIFIED -> StatusTone.POSITIVE
                sigState == SignatureState.MISMATCH -> StatusTone.NEGATIVE
                else -> StatusTone.WARNING
            },
        ) {
            if (installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        label = compat.status.display,
                        tone = when (compat.status) {
                            CompatStatus.SUPPORTED -> StatusTone.POSITIVE
                            CompatStatus.EXPERIMENTAL -> StatusTone.WARNING
                            CompatStatus.UNSUPPORTED -> StatusTone.NEGATIVE
                            CompatStatus.UNKNOWN -> StatusTone.NEUTRAL
                        },
                    )
                    if (sigState == SignatureState.VERIFIED) StatusPill("Signature verified", StatusTone.POSITIVE)
                }
                if (compat.note.isNotBlank()) {
                    Text(
                        compat.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sigState == SignatureState.UNTRUSTED || sigState == SignatureState.MISMATCH) {
                    Text(
                        "SHA-256: ${signature.digestHex?.take(32)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (installed && (sigState == SignatureState.UNTRUSTED || sigState == SignatureState.MISMATCH)) {
            Button(
                onClick = { core.signatures.trustCurrent() },
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) { Text(if (sigState == SignatureState.MISMATCH) "I understand — trust this signature" else "Trust this signature") }
        }

        // Requirements
        StatusCard(
            icon = if (overlayGranted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            title = "Overlay permission",
            subtitle = if (overlayGranted) "V Client can draw the HUD over the game."
            else "Required for the HUD, V Menu and effects.",
            tone = if (overlayGranted) StatusTone.POSITIVE else StatusTone.WARNING,
            actionLabel = if (overlayGranted) null else "Grant",
            onAction = if (overlayGranted) null else {
                {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            },
        )

        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
            StatusCard(
                icon = Icons.Rounded.CheckCircle,
                title = "Notifications",
                subtitle = "Shows the overlay session notification with quick actions.",
                tone = StatusTone.WARNING,
                actionLabel = "Allow",
                onAction = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }

        StatusCard(
            icon = Icons.Rounded.Accessibility,
            title = "Accessibility service (optional)",
            subtitle = if (VAccessibilityService.isConnected)
                "Connected — zoom, game-focus detection and touch mapping are active."
            else
                "Enables Zoom, auto-hiding the HUD outside the game, and real keystroke/CPS data on Android 14+.",
            tone = if (VAccessibilityService.isConnected) StatusTone.POSITIVE else StatusTone.NEUTRAL,
            actionLabel = if (VAccessibilityService.isConnected) null else "Open settings",
            onAction = if (VAccessibilityService.isConnected) null else {
                { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            },
        )

        // Launch
        Button(
            onClick = {
                if (!canLaunch) return@Button
                OverlayService.start(context)
                core.launcher.launch()
            },
            enabled = canLaunch,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Launch Minecraft", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            when {
                !installed -> "Minecraft isn't installed."
                !overlayGranted -> "Grant the overlay permission to launch."
                sigState == SignatureState.MISMATCH -> "Resolve the signature warning above to launch."
                else -> "Starts the overlay session, then opens the stock game — untouched."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (overlayRunning) {
                OutlinedButton(
                    onClick = { OverlayService.stop(context) },
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop overlay")
                }
                OutlinedButton(
                    onClick = { OverlayService.openMenu(context) },
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Open V Menu") }
            } else {
                OutlinedButton(
                    onClick = { OverlayService.start(context) },
                    enabled = overlayGranted,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Start overlay only") }
                OutlinedButton(
                    onClick = { nav.navigate(Routes.PROFILES) },
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Profiles") }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
