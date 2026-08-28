package com.venom.vclient.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venom.vclient.ui.Repos
import com.venom.vclient.ui.S
import com.venom.vclient.ui.VCard
import com.venom.vclient.ui.VChip

private val TREE = """
profiles/
└── <profile-id>/          ← isolated environment
    ├── env.json           profile identity
    ├── game/              versions · libraries · assets
    ├── saves/             world saves (per profile!)
    ├── mods/              this profile's mods only
    ├── resourcepacks/  shaderpacks/  config/
    ├── auth/session.json  isolated auth session
    ├── logs/              launch + game logs
    └── reports/           last full launch command
""".trimIndent()

@Composable
fun NewsScreen(repos: Repos) {
    val lang = repos.settings.language
    val t: (String) -> String = { S.t(lang, it) }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(t("news.title"), fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        item {
            VCard {
                Column {
                    Row {
                        VChip("v1.0.0", Modifier.padding(end = 8.dp))
                        Text(if (lang == "ar") "إصدار V Client 1.0" else "V Client 1.0 release", fontWeight = FontWeight.Bold)
                    }
                    val items = if (lang == "ar") {
                        listOf(
                            "بيئة معزولة كاملة لكل بروفايل",
                            "محرك V الداخلي لاختبار العزل بدون إنترنت",
                            "خط تشغيل ماينكرافت كامل → أمر تشغيل لـ Termux",
                            "حسابات Offline (name-hash)",
                            "واجهة عربية/إنجليزية مع RTL"
                        )
                    } else {
                        listOf(
                            "Full isolated environment per profile",
                            "Built-in V-Engine to test isolation offline",
                            "Full Minecraft pipeline → launch command for Termux",
                            "Offline accounts (name-hash)",
                            "Arabic/English UI with RTL"
                        )
                    }
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach {
                            Text("✓  " + it, color = Color(0xFFA1A1AA), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item {
            VCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("news.isolationTitle"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(t("news.isolationDesc"), color = Color(0xFFA1A1AA), fontSize = 13.sp)
                    val cards = if (lang == "ar") {
                        listOf(t("news.process") to t("news.processDesc"), t("news.data") to t("news.dataDesc"), t("news.session") to t("news.sessionDesc"))
                    } else {
                        listOf(t("news.process") to t("news.processDesc"), t("news.data") to t("news.dataDesc"), t("news.session") to t("news.sessionDesc"))
                    }
                    cards.forEach { (title, desc) ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(desc, color = Color(0xFFA1A1AA), fontSize = 12.sp)
                        }
                    }
                    Text(t("news.treeTitle"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFA1A1AA))
                    Text(
                        TREE,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFFD4D4D8),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
                    )
                    Text(t("news.legal"), color = Color(0xFF71717A), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
