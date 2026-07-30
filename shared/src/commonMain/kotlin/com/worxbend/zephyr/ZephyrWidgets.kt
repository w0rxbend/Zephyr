package com.worxbend.zephyr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class StatusTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Error,
}

internal fun statusSymbol(tone: StatusTone): String =
    when (tone) {
        StatusTone.Neutral -> "•"
        StatusTone.Accent -> "↻"
        StatusTone.Success -> "✓"
        StatusTone.Warning -> "!"
        StatusTone.Error -> "×"
    }

internal fun statusLabel(tone: StatusTone): String =
    when (tone) {
        StatusTone.Neutral -> "Unknown"
        StatusTone.Accent -> "In progress"
        StatusTone.Success -> "Healthy"
        StatusTone.Warning -> "Attention"
        StatusTone.Error -> "Error"
    }

@Composable
internal fun ZephyrPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    Surface(
        modifier = modifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            RoundedCornerShape(metrics.cornerRadius),
        ),
        shape = RoundedCornerShape(metrics.cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
internal fun ZephyrClickablePanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    Surface(
        modifier = modifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            RoundedCornerShape(metrics.cornerRadius),
        ),
        onClick = onClick,
        shape = RoundedCornerShape(metrics.cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
internal fun ZephyrSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun ZephyrNavigationItem(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val metrics = LocalZephyrMetrics.current
    var focused by remember { mutableStateOf(false) }
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.controlHeight)
            .background(background, RoundedCornerShape(metrics.cornerRadius))
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(metrics.cornerRadius),
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = contentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        badge?.let {
            Text(
                text = it,
                color = contentColor.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun ZephyrToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val metrics = LocalZephyrMetrics.current
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .height(metrics.controlHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(metrics.cornerRadius),
        border = BorderStroke(
            1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            detail?.let {
                StatusDot(tone = if (it == "failed" || it == "offline") StatusTone.Error else StatusTone.Accent)
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ZephyrMetricTile(
    label: String,
    value: String,
    detail: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalZephyrMetrics.current
    ZephyrPanel(modifier) {
        Column(
            modifier = Modifier.padding(metrics.panelPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusDot(tone)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ZephyrSettingsRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    control: @Composable RowScope.() -> Unit,
) {
    val metrics = LocalZephyrMetrics.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = metrics.spacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

@Composable
internal fun <T> ZephyrSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalZephyrMetrics.current
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(metrics.cornerRadius))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Text(
                text = label(option),
                modifier = Modifier
                    .height(metrics.controlHeight - 4.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape((metrics.cornerRadius - 2.dp).coerceAtLeast(2.dp)),
                    )
                    .semantics { this.selected = active }
                    .clickable(role = Role.RadioButton) { onSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun ZephyrToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
internal fun ZephyrDestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(label)
    }
}

@Composable
internal fun StatusDot(
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Accent -> MaterialTheme.colorScheme.primary
        StatusTone.Success -> Color(0xFF59A869)
        StatusTone.Warning -> Color(0xFFE2A53A)
        StatusTone.Error -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = modifier
            .size(14.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(99.dp))
            .semantics { contentDescription = "${statusLabel(tone)} status" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = statusSymbol(tone),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun ZephyrProgressIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) 18.dp else 40.dp
    if (LocalReducedMotion.current) {
        Box(
            modifier = modifier
                .size(size)
                .semantics { contentDescription = "In progress; reduced motion" },
            contentAlignment = Alignment.Center,
        ) {
            StatusDot(StatusTone.Accent)
        }
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            strokeWidth = if (compact) 2.dp else 4.dp,
        )
    }
}
