package com.worxbend.zephyr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.worxbend.zephyr.viewmodel.ZephyrUiState

internal enum class SearchOverlayMode {
    GlobalSearch,
    CommandPalette,
}

@Composable
internal fun GlobalSearchDialog(
    state: ZephyrUiState.Ready,
    mode: SearchOverlayMode = SearchOverlayMode.GlobalSearch,
    onDismiss: () -> Unit,
    onSelect: (GlobalSearchTarget) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val index = remember(state.candidates, state.catalog, mode) {
        val allItems = buildGlobalSearchIndex(state.candidates, state.catalog)
        if (mode == SearchOverlayMode.CommandPalette) commandPaletteItems(allItems) else allItems
    }
    val results = remember(index, query) { searchGlobalIndex(index, query) }
    var selectedIndex by remember(query) { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(results.size) {
        selectedIndex = selectedIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (mode == SearchOverlayMode.CommandPalette) "Command palette" else "Search Zephyr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Badge("ESC")
                }
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (mode == SearchOverlayMode.CommandPalette) {
                        "Search commands and destinations"
                    } else {
                        "Candidates, versions, settings, and actions"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (results.isNotEmpty()) {
                                            selectedIndex = (selectedIndex + 1).coerceAtMost(results.lastIndex)
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                        true
                                    }
                                    Key.Enter -> {
                                        results.getOrNull(selectedIndex)?.let { onSelect(it.target) }
                                        true
                                    }
                                    Key.Escape -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            }
                        },
                )
                Text(
                    if (query.isBlank()) {
                        if (mode == SearchOverlayMode.CommandPalette) "Available commands" else "Quick access"
                    } else {
                        "${results.size} result(s)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (results.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("No matching destination", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (mode == SearchOverlayMode.CommandPalette) {
                                "Try a workspace destination or maintenance action."
                            } else {
                                "Try a candidate key, version, setting, or maintenance action."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        itemsIndexed(results, key = { _, item -> item.id }) { indexInList, item ->
                            GlobalSearchResultRow(
                                item = item,
                                selected = indexInList == selectedIndex,
                                onClick = {
                                    selectedIndex = indexInList
                                    onSelect(item.target)
                                },
                            )
                        }
                    }
                }
                Text(
                    if (mode == SearchOverlayMode.CommandPalette) {
                        "↑↓ Move  •  Enter Run  •  Esc Close"
                    } else {
                        "↑↓ Move  •  Enter Open  •  Ctrl/⌘ Shift P Commands  •  Esc Close"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GlobalSearchResultRow(
    item: GlobalSearchItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(item.kind.label, if (selected) BadgeTone.Primary else BadgeTone.Neutral)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                item.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (item.shortcut != null) {
            Badge(item.shortcut, if (selected) BadgeTone.Primary else BadgeTone.Neutral)
        } else if (selected) {
            Badge("Enter", BadgeTone.Primary)
        }
    }
}
