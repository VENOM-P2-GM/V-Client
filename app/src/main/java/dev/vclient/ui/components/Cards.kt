package dev.vclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class StatusTone { POSITIVE, WARNING, NEGATIVE, NEUTRAL, ACCENT }

@Composable
fun statusColor(tone: StatusTone): Color = when (tone) {
    StatusTone.POSITIVE -> Color(0xFF34D399)
    StatusTone.WARNING -> Color(0xFFFBBF24)
    StatusTone.NEGATIVE -> Color(0xFFF87171)
    StatusTone.ACCENT -> MaterialTheme.colorScheme.secondary
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Rounded card with an icon, title, subtitle and an optional action. */
@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tone: StatusTone = StatusTone.NEUTRAL,
    actionLabel: String? = null,
    secondaryActionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = statusColor(tone), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
            if (actionLabel != null || secondaryActionLabel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (actionLabel != null && onAction != null) {
                        Button(onClick = onAction, modifier = Modifier.weight(1f)) {
                            Text(actionLabel, maxLines = 1)
                        }
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                            Text(secondaryActionLabel, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Small pill with a colored dot + label (compat status, signature state...). */
@Composable
fun StatusPill(label: String, tone: StatusTone) {
    Surface(
        color = statusColor(tone).copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(7.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(statusColor(tone)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(tone),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
