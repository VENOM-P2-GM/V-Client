package com.venom.vclient.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venom.vclient.ui.Repos
import com.venom.vclient.ui.S
import com.venom.vclient.ui.VButton
import com.venom.vclient.ui.VCard
import com.venom.vclient.ui.VChip
import com.venom.vclient.ui.VEmpty

@Composable
fun AccountsScreen(repos: Repos) {
    val lang = repos.settings.language
    val t: (String) -> String = { S.t(lang, it) }
    var tick by remember { mutableIntStateOf(0) }
    val accounts = remember(tick) { repos.accounts.list() }
    var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(t("accounts.title"), fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        item {
            VCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("accounts.offline"), fontWeight = FontWeight.Bold)
                    Text(t("accounts.offlineDesc"), color = Color(0xFFA1A1AA), fontSize = 12.sp)
                    OutlinedTextField(
                        name,
                        onValueChange = { name = it },
                        label = { Text(t("accounts.name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err.isNotBlank()) Text(err, color = Color(0xFFF87171), fontSize = 12.sp)
                    VButton(
                        t("accounts.add"),
                        {
                            err = ""
                            try {
                                repos.accounts.addOffline(name)
                                name = ""
                                tick++
                            } catch (e: Exception) {
                                err = e.message ?: "error"
                            }
                        },
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (accounts.isEmpty()) {
            item { VEmpty(t("accounts.empty"), t("accounts.emptyDesc")) }
        }
        items(accounts) { a ->
            VCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBox(a.name, a.uuid)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, fontWeight = FontWeight.Bold)
                            VChip(
                                a.type,
                                Modifier.padding(start = 8.dp),
                                if (a.type == "microsoft") Color(0xFFA3E635) else Color(0xFFA1A1AA)
                            )
                        }
                        Text(a.uuid, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF71717A))
                    }
                    Icon(
                        Icons.Outlined.Delete,
                        null,
                        tint = Color(0xFF71717A),
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(20.dp)
                            .clickable {
                                repos.accounts.remove(a.id)
                                tick++
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarBox(name: String, uuid: String) {
    val hue = if (uuid.length >= 8) (uuid.replace("-", "").substring(0, 6).toIntOrNull(16) ?: 260) % 360 else 260
    val c = Color.Hsv(hue / 360f, 0.65f, 0.55f)
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c),
        contentAlignment = Alignment.Center
    ) {
        Text(name.substring(0, 1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}
