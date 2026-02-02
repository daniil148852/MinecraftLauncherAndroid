package com.mclauncher.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.domain.models.VersionType
import com.mclauncher.ui.components.VersionItem
import com.mclauncher.ui.theme.MCColors
import com.mclauncher.viewmodels.VersionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(
    onNavigateBack: () -> Unit,
    onVersionClick: (String) -> Unit,
    viewModel: VersionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (showSearch) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::search,
                            placeholder = { Text("Search versions...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    } else {
                        Text("Versions")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showSearch) {
                            showSearch = false
                            viewModel.search("")
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (showSearch) Icons.Filled.Close else Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Badge(
                            modifier = Modifier.offset(x = 8.dp, y = (-8).dp),
                            containerColor = if (hasActiveFilters(uiState)) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surface
                        ) {
                            if (hasActiveFilters(uiState)) {
                                Text(getActiveFilterCount(uiState).toString())
                            }
                        }
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.versions.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Version Stats
                    VersionStats(
                        total = uiState.versions.size,
                        installed = uiState.installedVersions.size,
                        filtered = uiState.filteredVersions.size,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Latest Versions Cards
                    if (uiState.latestRelease != null || uiState.latestSnapshot != null) {
                        LatestVersionsRow(
                            latestRelease = uiState.latestRelease,
                            latestSnapshot = uiState.latestSnapshot,
                            downloadingVersions = uiState.downloadingVersions,
                            downloadProgress = uiState.downloadProgress,
                            onDownload = viewModel::downloadVersion,
                            onCancelDownload = viewModel::cancelDownload,
                            onClick = onVersionClick,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Version List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.filteredVersions,
                            key = { it.id }
                        ) { version ->
                            VersionItem(
                                version = version,
                                isDownloading = uiState.downloadingVersions.contains(version.id),
                                downloadProgress = uiState.downloadProgress[version.id] ?: 0f,
                                onClick = { onVersionClick(version.id) },
                                onDownload = { viewModel.downloadVersion(version.id) },
                                onCancelDownload = { viewModel.cancelDownload(version.id) },
                                onDelete = { viewModel.deleteVersion(version.id) }
                            )
                        }

                        if (uiState.filteredVersions.isEmpty()) {
                            item {
                                EmptyState(
                                    hasFilters = hasActiveFilters(uiState),
                                    onClearFilters = {
                                        viewModel.setFilter(
                                            showReleases = true,
                                            showSnapshots = false,
                                            showOldBeta = false,
                                            showOldAlpha = false,
                                            showInstalledOnly = false
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Pull to refresh indicator
                if (uiState.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }

            // Error Snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::dismissError) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            showReleases = uiState.showReleases,
            showSnapshots = uiState.showSnapshots,
            showOldBeta = uiState.showOldBeta,
            showOldAlpha = uiState.showOldAlpha,
            showInstalledOnly = uiState.showInstalledOnly,
            onFilterChange = { releases, snapshots, oldBeta, oldAlpha, installed ->
                viewModel.setFilter(releases, snapshots, oldBeta, oldAlpha, installed)
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun VersionStats(
    total: Int,
    installed: Int,
    filtered: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatChip(label = "Total", value = total.toString())
        StatChip(label = "Installed", value = installed.toString())
        StatChip(label = "Showing", value = filtered.toString())
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LatestVersionsRow(
    latestRelease: GameVersion?,
    latestSnapshot: GameVersion?,
    downloadingVersions: Set<String>,
    downloadProgress: Map<String, Float>,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        latestRelease?.let { version ->
            LatestVersionCard(
                title = "Latest Release",
                version = version,
                isDownloading = downloadingVersions.contains(version.id),
                downloadProgress = downloadProgress[version.id] ?: 0f,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onDownload = { onDownload(version.id) },
                onCancelDownload = { onCancelDownload(version.id) },
                onClick = { onClick(version.id) },
                modifier = Modifier.weight(1f)
            )
        }
        latestSnapshot?.let { version ->
            LatestVersionCard(
                title = "Latest Snapshot",
                version = version,
                isDownloading = downloadingVersions.contains(version.id),
                downloadProgress = downloadProgress[version.id] ?: 0f,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onDownload = { onDownload(version.id) },
                onCancelDownload = { onCancelDownload(version.id) },
                onClick = { onClick(version.id) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LatestVersionCard(
    title: String,
    version: GameVersion,
    isDownloading: Boolean,
    downloadProgress: Float,
    containerColor: androidx.compose.ui.graphics.Color,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = version.id,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onCancelDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (version.isInstalled) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Installed",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        TextButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    showReleases: Boolean,
    showSnapshots: Boolean,
    showOldBeta: Boolean,
    showOldAlpha: Boolean,
    showInstalledOnly: Boolean,
    onFilterChange: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Filter Versions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            FilterSwitch(
                label = "Releases",
                description = "Stable versions",
                checked = showReleases,
                onCheckedChange = { 
                    onFilterChange(it, showSnapshots, showOldBeta, showOldAlpha, showInstalledOnly)
                }
            )
            
            FilterSwitch(
                label = "Snapshots",
                description = "Development versions",
                checked = showSnapshots,
                onCheckedChange = { 
                    onFilterChange(showReleases, it, showOldBeta, showOldAlpha, showInstalledOnly)
                }
            )
            
            FilterSwitch(
                label = "Old Beta",
                description = "Beta versions (2010-2011)",
                checked = showOldBeta,
                onCheckedChange = { 
                    onFilterChange(showReleases, showSnapshots, it, showOldAlpha, showInstalledOnly)
                }
            )
            
            FilterSwitch(
                label = "Old Alpha",
                description = "Alpha versions (2010)",
                checked = showOldAlpha,
                onCheckedChange = { 
                    onFilterChange(showReleases, showSnapshots, showOldBeta, it, showInstalledOnly)
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            FilterSwitch(
                label = "Installed Only",
                description = "Show only downloaded versions",
                checked = showInstalledOnly,
                onCheckedChange = { 
                    onFilterChange(showReleases, showSnapshots, showOldBeta, showOldAlpha, it)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (hasFilters) "No versions match your filters" else "No versions found",
            style = MaterialTheme.typography.bodyLarge
        )
        if (hasFilters) {
            OutlinedButton(onClick = onClearFilters) {
                Text("Clear Filters")
            }
        }
    }
}

private fun hasActiveFilters(state: com.mclauncher.viewmodels.VersionsUiState): Boolean {
    return state.showSnapshots || state.showOldBeta || state.showOldAlpha || state.showInstalledOnly
}

private fun getActiveFilterCount(state: com.mclauncher.viewmodels.VersionsUiState): Int {
    var count = 0
    if (state.showSnapshots) count++
    if (state.showOldBeta) count++
    if (state.showOldAlpha) count++
    if (state.showInstalledOnly) count++
    return count
}
