package com.worxbend.zephyr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worxbend.zephyr.domain.CandidateKind
import com.worxbend.zephyr.domain.OperationStatus
import com.worxbend.zephyr.viewmodel.ZephyrUiState
import org.jetbrains.compose.resources.painterResource
import zephyr.shared.generated.resources.Res
import zephyr.shared.generated.resources.ic_jdk
import zephyr.shared.generated.resources.ic_sdk

@Composable
internal fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalZephyrMetrics.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .height(metrics.controlHeight)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, borderColor, RoundedCornerShape(metrics.cornerRadius))
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "⌕",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Text(
                        text = "×",
                        modifier = Modifier.clickable(role = Role.Button) { onValueChange("") }.padding(horizontal = 3.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    )
}

@Composable
internal fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { text.withLinks(linkColor) }
    Text(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun String.withLinks(linkColor: Color): AnnotatedString {
    val urlPattern = Regex("""https?://[^\s)]+""")
    return buildAnnotatedString {
        append(this@withLinks)
        urlPattern.findAll(this@withLinks).forEach { match ->
            addLink(
                LinkAnnotation.Url(
                    url = match.value.trimEnd('.', ','),
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
                match.range.first,
                match.range.last + 1,
            )
        }
    }
}

@Composable
internal fun AccordionHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    onClick: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val metrics = LocalZephyrMetrics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(metrics.cornerRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (collapsed) "›" else "⌄", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Badge("$count")
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun EmptyState(title: String, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun CandidateIcon(kind: CandidateKind) {
    val size = if (LocalZephyrMetrics.current.controlHeight <= 32.dp) 42.dp else 48.dp
    Box(
        Modifier.size(size).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (kind == CandidateKind.Jdk) Res.drawable.ic_jdk else Res.drawable.ic_sdk),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

internal enum class BadgeTone {
    Neutral,
    Primary,
    Success,
    Warning,
}

@Composable
internal fun Badge(text: String, tone: BadgeTone = BadgeTone.Neutral) {
    val background = when (tone) {
        BadgeTone.Neutral -> MaterialTheme.colorScheme.secondaryContainer
        BadgeTone.Primary -> MaterialTheme.colorScheme.primaryContainer
        BadgeTone.Success -> Color(0xFFDBF0DF)
        BadgeTone.Warning -> Color(0xFFF8E6C2)
    }
    val foreground = when (tone) {
        BadgeTone.Neutral -> MaterialTheme.colorScheme.onSecondaryContainer
        BadgeTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
        BadgeTone.Success -> Color(0xFF166534)
        BadgeTone.Warning -> Color(0xFF92400E)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
internal fun CodeBlock(text: String) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
    )
}

@Composable
internal fun MessageOverlay(
    state: ZephyrUiState.Ready,
    onDismiss: () -> Unit,
    onOpenRecovery: (() -> Unit)? = null,
) {
    val message = state.errorMessage ?: state.lastOutcome ?: return
    val hasFailedOperation = state.errorMessage != null &&
        state.operationJournal.firstOrNull()?.status == OperationStatus.Failed
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(10_000)
        onDismiss()
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (hasFailedOperation && onOpenRecovery != null) {
                TextButton(onClick = onOpenRecovery) { Text("Recovery steps") }
            }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
internal fun BusyOverlay(state: ZephyrUiState.Ready) {
    val label = when {
        state.localOnlyScanInProgress -> "Scanning local-only versions"
        state.isCatalogLoading -> "Loading SDKMAN catalog"
        state.detailLoadingCandidate != null -> "Loading package details"
        state.transactionPreviewLoading -> "Calculating disk impact"
        state.diagnosticsExportInProgress -> "Exporting support bundle"
        state.isRefreshing -> "Refreshing"
        else -> return
    }
    Box(
        Modifier.fillMaxSize().padding(top = LocalZephyrMetrics.current.toolbarHeight + 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
