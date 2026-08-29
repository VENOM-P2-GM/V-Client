package dev.vclient.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.vclient.core.VClientCore

/**
 * Log viewer: live log ring buffer plus persisted log files and crash
 * reports, all from the isolated logs/crashes directories. Share via the
 * system share sheet.
 */
@Composable
fun LogsScreen(nav: NavController) {
    val core = VClientCore.core
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<String?>(null) }

    val files = remember(selectedFile) {
        (core.logger.logFiles() + core.crashReporter.crashFiles()).sortedByDescending { it.lastModified() }
    }
    val recent = remember(selectedFile) {
        selectedFile?.let { name -> files.firstOrNull { it.name == name }?.let { readTail(it, 300) } }
            ?: core.logger.recentLines(200)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.padding(top = 12.dp)) {
                Text("Logs & crashes", style = MaterialTheme.typography.titleLarge)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedFile == null, onClick = { selectedFile = null }, label = { Text("Live") })
            items.take(4).forEach { file ->
                FilterChip(
                    selected = selectedFile == file.name,
                    onClick = { selectedFile = file.name },
                    label = { Text(file.name.take(22)) },
                )
            }
        }

        if (selectedFile != null) {
            val target = files.firstOrNull { it.name == selectedFile }
            if (target != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FilterChip(selected = false, onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, readTail(target, 2000))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }, label = { Text("Share") })
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(recent) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            line.contains(" E [") -> MaterialTheme.colorScheme.error
                            line.contains(" W [") -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

private fun readTail(file: java.io.File, maxLines: Int): List<String> =
    runCatching { file.readLines().takeLast(maxLines) }.getOrDefault(emptyList())
