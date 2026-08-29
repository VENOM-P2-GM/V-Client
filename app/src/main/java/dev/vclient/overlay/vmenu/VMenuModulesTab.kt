package dev.vclient.overlay.vmenu

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vclient.core.VClientCore
import dev.vclient.core.module.Module
import dev.vclient.core.module.ModuleCategory
import dev.vclient.ui.widgets.ModuleRow
import dev.vclient.ui.widgets.SettingRow

/** Modules tab: searchable, category-grouped list + a detail page per module. */
@Composable
fun ModulesTab() {
    val core = VClientCore.core
    var query by remember { mutableStateOf("") }
    var openModuleId by remember { mutableStateOf<String?>(null) }

    val openModule = openModuleId?.let { core.modules.byId(it) }
    if (openModule != null) {
        ModuleDetail(module = openModule, onBack = { openModuleId = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search modules") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        val filtered = core.modules.all.filter {
            it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }
        LazyColumn(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            ModuleCategory.entries.forEach { category ->
                val modules = filtered.filter { it.category == category }
                if (modules.isEmpty()) return@forEach
                item(key = "cat_$category") { SectionTitle(category.display) }
                items(modules, key = { it.id }) { module ->
                    ModuleRow(module, onClick = { openModuleId = module.id })
                }
            }
        }
    }
}

/** Detail page: description, master toggle, all settings auto-rendered. */
@Composable
fun ModuleDetail(module: Module, onBack: () -> Unit) {
    val enabled by module.enabledFlow.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(module.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    module.category.display,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Switch(checked = enabled, onCheckedChange = { module.setEnabled(it) })
        }
        Text(
            module.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(8.dp))
        LazyColumn(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (module.settings.all.isEmpty()) {
                item { Text("This module has no settings yet.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(module.settings.all, key = { it.id }) { setting ->
                    SettingRow(setting)
                }
            }
        }
    }
}
