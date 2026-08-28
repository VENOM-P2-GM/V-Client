package com.venom.vclient.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PuzzlePiece
import androidx.compose.material.icons.outlined.Rss
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venom.vclient.core.AccountsRepository
import com.venom.vclient.core.ProfilesRepository
import com.venom.vclient.core.SettingsStore
import com.venom.vclient.ui.screens.AccountsScreen
import com.venom.vclient.ui.screens.HomeScreen
import com.venom.vclient.ui.screens.ModsScreen
import com.venom.vclient.ui.screens.NewsScreen
import com.venom.vclient.ui.screens.ProfilesScreen
import com.venom.vclient.ui.screens.SettingsScreen

class Repos(private val ctx: Context) {
    val settings = SettingsStore(ctx)
    val profiles = ProfilesRepository(ctx)
    val accounts = AccountsRepository(ctx)
}

@Composable
fun App(repos: Repos) {
    var tab by rememberSaveable { mutableStateOf("home") }
    val lang = repos.settings.language
    Scaffold(
        containerColor = Color(0xFF0A0A14),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("V", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        }
                        Column {
                            Text("V Client", fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text("VENOM-P2-GM", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0E1C))
            )
        },
        bottomBar = {
            Surface(color = Color(0xFF0E0E1C), tonalElevation = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    NavItem(Icons.Outlined.Home, S.t(lang, "nav.home"), tab == "home") { tab = "home" }
                    NavItem(Icons.Outlined.Folder, S.t(lang, "nav.profiles"), tab == "profiles") { tab = "profiles" }
                    NavItem(Icons.Outlined.PuzzlePiece, S.t(lang, "nav.mods"), tab == "mods") { tab = "mods" }
                    NavItem(Icons.Outlined.People, S.t(lang, "nav.accounts"), tab == "accounts") { tab = "accounts" }
                    NavItem(Icons.Outlined.Settings, S.t(lang, "nav.settings"), tab == "settings") { tab = "settings" }
                    NavItem(Icons.Outlined.Rss, S.t(lang, "nav.news"), tab == "news") { tab = "news" }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                "home" -> HomeScreen(repos)
                "profiles" -> ProfilesScreen(repos)
                "mods" -> ModsScreen(repos)
                "accounts" -> AccountsScreen(repos)
                "settings" -> SettingsScreen(repos)
                "news" -> NewsScreen(repos)
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF71717A)
    Column(
        Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = color, maxLines = 1)
    }
}
