package dev.vclient.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vclient.core.VClientCore
import dev.vclient.core.module.Module
import dev.vclient.core.module.ModuleCategory
import dev.vclient.overlay.vmenu.ModuleDetail
import dev.vclient.ui.widgets.ModuleRow

/** Launcher module manager — same live data as the in-game V Menu. */
@Composable
fun ModulesScreen() {
    val core = VClientCore.core
    var openModuleId by remember { mutableStateOf<String?>(null) }

    val openModule: Module? = openModuleId?.let { core.modules.byId(it) }
    if (openModule != null) {
        ModuleDetail(module = openModule, onBack = { openModuleId = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Modules",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Text(
            "${core.modules.enabledCount} of ${core.modules.all.size} enabled · changes save to '${core.profiles.activeName.value}'",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        ) {
            ModuleCategory.entries.forEach { category ->
                val modules = core.modules.byCategory(category)
                if (modules.isEmpty()) return@forEach
                item(key = "cat_$category") {
                    Text(
                        category.display.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(modules, key = { it.id }) { module ->
                    ModuleRow(module, onClick = { openModuleId = module.id }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
