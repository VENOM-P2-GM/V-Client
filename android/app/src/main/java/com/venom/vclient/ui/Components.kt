package com.venom.vclient.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x0AFFFFFF),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun VButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VProgress(value: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0x1AFFFFFF), RoundedCornerShape(4.dp))
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0, 100) / 100f)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun VChip(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VField(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFA1A1AA),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

/** Dialog-based options picker (no experimental APIs). */
@Composable
fun Picker(options: List<String>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(selected.ifBlank { "—" }, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ArrowDropDown, null, tint = Color(0xFFA1A1AA))
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = null,
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    items(options) { opt ->
                        ListItem(
                            headlineContent = { Text(opt, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            trailingContent = {
                                if (opt == selected) {
                                    Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onPick(opt)
                                open = false
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun VLog(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    Surface(
        modifier = modifier.fillMaxWidth().height(230.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF0050508)
    ) {
        LazyColumn(state = listState, modifier = Modifier.padding(10.dp)) {
            items(lines.size) { i ->
                Text(
                    lines[i].ifBlank { " " },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFD4D4D8),
                    softWrap = true
                )
            }
        }
    }
}

@Composable
fun VEmpty(title: String, desc: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("◈", fontSize = 34.sp, color = MaterialTheme.colorScheme.primary)
        Text(title, fontWeight = FontWeight.Extrabold, modifier = Modifier.padding(top = 8.dp))
        Text(desc, color = Color(0xFFA1A1AA), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, horizontal = 24.dp))
    }
}

fun humanSize(n: Long): String {
    if (n < 1024) return "$n B"
    var v = n.toDouble()
    val units = arrayOf("KB", "MB", "GB", "TB")
    var i = -1
    do {
        v /= 1024
        i++
    } while (v >= 1024 && i < units.size - 1)
    return if (v >= 100) "${v.toLong()} ${units[i]}" else "${"%.1f".format(v)} ${units[i]}"
}
