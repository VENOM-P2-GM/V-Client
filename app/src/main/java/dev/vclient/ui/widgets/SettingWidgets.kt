package dev.vclient.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vclient.core.module.Module
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.ColorSetting
import dev.vclient.core.settings.FloatSetting
import dev.vclient.core.settings.IntSetting
import dev.vclient.core.settings.ModeSetting
import dev.vclient.core.settings.Setting
import dev.vclient.core.settings.TextSetting
import kotlin.math.roundToInt

/**
 * Auto-rendering UI for module settings. One row per setting; values write
 * straight back into the setting (which triggers profile auto-save) and the
 * Compose UI follows the setting's StateFlow.
 */
@Composable
fun SettingRow(setting: Setting<*>, modifier: Modifier = Modifier) {
    when (setting) {
        is BoolSetting -> BoolRow(setting, modifier)
        is IntSetting -> IntRow(setting, modifier)
        is FloatSetting -> FloatRow(setting, modifier)
        is ColorSetting -> ColorRow(setting, modifier)
        is ModeSetting -> ModeRow(setting, modifier)
        is TextSetting -> TextRow(setting, modifier)
    }
}

@Composable
private fun RowHeader(name: String, value: String?, description: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
    if (description.isNotBlank()) {
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoolRow(setting: BoolSetting, modifier: Modifier) {
    val value by setting.state.collectAsState()
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(setting.name, style = MaterialTheme.typography.titleSmall)
            if (setting.description.isNotBlank()) {
                Text(
                    setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = value, onCheckedChange = { setting.value = it })
    }
}

@Composable
private fun IntRow(setting: IntSetting, modifier: Modifier) {
    val value by setting.state.collectAsState()
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RowHeader(setting.name, "$value${setting.suffix}", setting.description)
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                setting.value = ((raw / setting.step).roundToInt() * setting.step)
                    .coerceIn(setting.min, setting.max)
            },
            valueRange = setting.min.toFloat()..setting.max.toFloat(),
        )
    }
}

@Composable
private fun FloatRow(setting: FloatSetting, modifier: Modifier) {
    val value by setting.state.collectAsState()
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RowHeader(setting.name, "%.${setting.decimals}f${setting.suffix}".format(value), setting.description)
        Slider(
            value = value,
            onValueChange = { raw -> setting.value = (raw / setting.step).roundToInt() * setting.step },
            valueRange = setting.min..setting.max,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeRow(setting: ModeSetting, modifier: Modifier) {
    val value by setting.state.collectAsState()
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RowHeader(setting.name, null, setting.description)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            setting.options.forEach { option ->
                FilterChip(
                    selected = option == value,
                    onClick = { setting.value = option },
                    label = { Text(option) },
                )
            }
        }
    }
}

@Composable
private fun TextRow(setting: TextSetting, modifier: Modifier) {
    var text by remember(setting.id) { mutableStateOf(setting.state.value) }
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RowHeader(setting.name, null, setting.description)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                setting.value = it
            },
            placeholder = { Text(setting.hint.ifBlank { setting.name }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val ColorPresets = listOf(
    0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt(), 0xFF34D399.toInt(), 0xFFFBBF24.toInt(),
    0xFFF87171.toInt(), 0xFF60A5FA.toInt(), 0xFFF472B6.toInt(), 0xFFE6E9F2.toInt(),
    0xFF0B0E14.toInt(), 0xFFFFFFFF.toInt(),
)

@Composable
private fun ColorRow(setting: ColorSetting, modifier: Modifier) {
    val value by setting.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                RowHeader(setting.name, "#%08X".format(value), setting.description)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
        if (expanded) {
            ColorPicker(current = value) { setting.value = it }
        }
    }
}

/** Compact picker: preset palette + hue/alpha sliders. */
@Composable
fun ColorPicker(current: Int, onPick: (Int) -> Unit) {
    val hsv = remember(current) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(current, out)
        out
    }
    val alpha = remember(current) { (current ushr 24) / 255f }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var valueSat by remember { mutableStateOf(hsv[2]) }
    var alphaState by remember { mutableStateOf(alpha) }

    fun build(): Int {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, valueSat))
        return (alphaState.toInt().coerceIn(0, 255) shl 24) or (rgb and 0x00FFFFFF)
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(build())),
            verticalAlignment = Alignment.CenterVertically,
        ) {}
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPresets.forEach { preset ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (preset == current) 2.dp else 1.dp,
                            color = if (preset == current) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable {
                            val h = FloatArray(3)
                            android.graphics.Color.colorToHSV(preset, h)
                            hue = h[0]; sat = h[1]; valueSat = h[2]
                            alphaState = ((preset ushr 24) / 255f).coerceAtLeast(0.35f)
                            onPick(build())
                        },
                )
            }
        }
        LabeledSlider("Hue", hue, 0f..360f) { hue = it; onPick(build()) }
        LabeledSlider("Saturation", sat, 0f..1f) { sat = it; onPick(build()) }
        LabeledSlider("Brightness", valueSat, 0f..1f) { valueSat = it; onPick(build()) }
        LabeledSlider("Opacity", alphaState, 0f..1f) { alphaState = it; onPick(build()) }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "$label  ${(value * 100).roundToInt()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

/** Standard module row: name/description + toggle switch; whole row clickable. */
@Composable
fun ModuleRow(module: Module, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val enabled by module.enabledFlow.collectAsState()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(module.name, style = MaterialTheme.typography.titleSmall)
            Text(
                module.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = enabled, onCheckedChange = { module.setEnabled(it) })
    }
}

/** Helper for numeric text fields (manual position entry etc.). */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> if (raw.matches(Regex("^-?\\d*\\.?\\d*$"))) onValueChange(raw) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
