package dev.netvalve.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.graphics.drawable.toBitmap
import dev.netvalve.data.model.SelectionMode
import dev.netvalve.scheduler.PriorityLevels
import dev.netvalve.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onOpenApp: (String) -> Unit,
    viewModel: AppSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val tierMessage by viewModel.tierMessage.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Apps") })

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Text(
            if (mode == SelectionMode.ONLY_SELECTED) {
                "Ticked apps go through NetValve and can be controlled. Unticked apps are left alone."
            } else {
                "Ticked apps are EXCLUDED from NetValve. All other apps are controlled."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Text(
            "Priority chip of a controlled app (tap it): adds the app at the end of the priority list " +
                "(at most ${PriorityLevels.MAX_RANKS} apps) or takes it out. The order is set on the Apps tab. " +
                "NetValve itself is always normal and PayProbe always the most important.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        tierMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextButton(onClick = { viewModel.setAllShown(true) }) { Text("Tick all") }
            androidx.compose.material3.TextButton(onClick = { viewModel.setAllShown(false) }) { Text("Clear all") }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = state.includeSystem,
                onClick = { viewModel.setIncludeSystem(!state.includeSystem) },
                label = { Text("Show system apps") },
            )
        }

        if (state.pendingRestart) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Selection changed — restart tunnel to apply", color = MaterialTheme.colorScheme.tertiary)
                androidx.compose.material3.TextButton(onClick = viewModel::applyChanges) { Text("Apply") }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.apps.isEmpty() -> EmptyState(
                icon = Icons.Filled.Search,
                title = "No apps match",
                subtitle = "Try a different search, or enable system apps.",
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.apps, key = { it.app.packageName }) { row ->
                    AppRow(
                        label = row.app.label,
                        packageName = row.app.packageName,
                        isSystem = row.app.isSystem,
                        selected = row.selected,
                        // ONLY_SELECTED: ticked = controlled. ALL_EXCEPT: ticked = excluded.
                        controlled = if (mode == SelectionMode.ONLY_SELECTED) row.selected else !row.selected,
                        rank = row.rank,
                        tierLocked = row.locked,
                        onTierClick = { viewModel.togglePriority(row.app.packageName) },
                        onToggle = { viewModel.toggle(row.app.packageName, it) },
                        onOpen = { onOpenApp(row.app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    label: String,
    packageName: String,
    isSystem: Boolean,
    selected: Boolean,
    controlled: Boolean,
    rank: Int,
    tierLocked: Boolean,
    onTierClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!selected) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName, label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isSystem) "$packageName · system" else packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (controlled) {
                AssistChip(
                    onClick = onTierClick,
                    enabled = !tierLocked,
                    label = { Text(rankLabel(rank, tierLocked)) },
                )
            } else {
                Text(
                    "Not controlled (Priority Control does not apply)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onOpen) { Icon(Icons.Filled.Tune, contentDescription = "Configure") }
        Checkbox(checked = selected, onCheckedChange = onToggle)
    }
}

private fun rankLabel(rank: Int, locked: Boolean): String {
    val name = if (rank > 0) "Priority #$rank" else "Normal"
    return if (locked) "$name (fixed)" else name
}

@Composable
internal fun AppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = label, modifier = Modifier.size(36.dp))
    } else {
        LetterAvatar(label)
    }
}

@Composable
private fun LetterAvatar(label: String) {
    Box(
        Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
