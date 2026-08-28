package com.venom.vclient.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venom.vclient.ui.Repos
import com.venom.vclient.ui.S
import com.venom.vclient.ui.VButton
import com.venom.vclient.ui.VCard
import com.venom.vclient.ui.VField
import com.venom.vclient.ui.VProgress

@Composable
fun SettingsScreen(repos: Repos) {
    val lang = repos.settings.language
    val t: (String) -> String = { S.t(lang, it) }
    var tick by remember { mutableIntStateOf(0) }
    val s = remember(tick) { repos.settings }
    var memMax by remember { mutableIntStateOf(s.defaultMemoryMax) }
    var memMin by remember { mutableIntStateOf(s.defaultMemoryMin) }
    var assetMode by remember { mutableStateOf(s.assetMode) }
    var msClient by remember { mutableStateOf(s.msClientId) }
    var langSel by remember { mutableStateOf(s.language) }
    var themeSel by remember { mutableStateOf(s.theme) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            kotlinx.coroutines.delay(1500)
            saved = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(t("settings.title"), fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        item {
            VCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VField(t("settings.language")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ar" to "العربية", "en" to "English").forEach { (id, label) ->
                                OptionPill(label, langSel == id) { langSel = id }
                            }
                        }
                    }
                    VField(t("settings.theme")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OptionPill(t("settings.themeVenom"), themeSel == "venom") { themeSel = "venom" }
                            OptionPill(t("settings.themeToxic"), themeSel == "toxic") { themeSel = "toxic" }
                        }
                    }
                    VField(t("settings.dataRoot")) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1A000000))
                                .padding(10.dp)
                        ) {
                            Text(s.dataRoot(), fontSize = 11.sp, color = Color(0xFFA1A1AA))
                        }
                    }
                }
            }
        }
        item {
            VCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VField(t("settings.memMax") + " — " + memMax) {
                        Slider(memMax.toFloat(), 16384f, onValueChange = { memMax = it.toInt() })
                    }
                    VField(t("settings.memMin") + " — " + memMin) {
                        Slider(memMin.toFloat(), 8192f, onValueChange = { memMin = it.toInt() })
                    }
                    VField(t("settings.assetMode")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OptionPill(t("settings.assetShared"), assetMode == "shared") { assetMode = "shared" }
                            OptionPill(t("settings.assetIsolated"), assetMode == "isolated") { assetMode = "isolated" }
                        }
                    }
                }
            }
        }
        item {
            VCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("settings.termuxTitle"), fontWeight = FontWeight.Bold)
                    Text(t("settings.termuxDesc"), color = Color(0xFFA1A1AA), fontSize = 12.sp)
                }
            }
        }
        item {
            VCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VField(t("settings.msClient")) {
                        OutlinedTextField(
                            msClient,
                            onValueChange = { msClient = it },
                            label = { Text("xxxxxxxx-xxxx-xxxx") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(t("settings.msClientDesc"), color = Color(0xFF71717A), fontSize = 11.sp)
                }
            }
        }
        item {
            Row {
                VButton(
                    t("settings.save"),
                    {
                        repos.settings.update(
                            memoryMax = memMax,
                            memoryMin = memMin,
                            assetMode = assetMode,
                            msClientId = msClient,
                            language = langSel,
                            theme = themeSel
                        )
                        tick++
                        saved = true
                    },
                    Modifier.weight(1f)
                )
            }
            if (saved) {
                Text("✓", color = Color(0xFFA3E635), fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun OptionPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0x0AFFFFFF))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFA1A1AA))
    }
}
