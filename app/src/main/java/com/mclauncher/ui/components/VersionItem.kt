package com.mclauncher.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.domain.models.VersionType
import com.mclauncher.ui.theme.MCColors

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VersionItem(
    version: GameVersion,
    isDownloading: Boolean,
    downloadProgress: Float,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Version Type Badge
                VersionTypeBadge(type = version.type)

                Spacer(modifier = Modifier.width(12.dp))

                // Version Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = version.id,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatReleaseTime(version.releaseTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status/Actions
                Box {
                    when {
                        isDownloading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(onClick = onCancelDownload) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Cancel",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        version.isInstalled -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Installed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Installed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = "More"
                                    )
                                }
                            }
                        }
                        else -> {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Details") },
                            onClick = {
                                showMenu = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Info, contentDescription = null)
                            }
                        )
                        
                        if (version.isInstalled) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error
                                )
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Download") },
                                onClick = {
                                    showMenu = false
                                    onDownload()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Download, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }

            // Download Progress Bar
            AnimatedVisibility(visible = isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Version?") },
            text = {
                Text("Are you sure you want to delete ${version.id}? This will remove all downloaded files for this version.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VersionTypeBadge(
    type: VersionType,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (type) {
        VersionType.RELEASE -> Pair(MCColors.version_release, "Release")
        VersionType.SNAPSHOT -> Pair(MCColors.version_snapshot, "Snapshot")
        VersionType.OLD_BETA -> Pair(MCColors.version_old_beta, "Beta")
        VersionType.OLD_ALPHA -> Pair(MCColors.version_old_alpha, "Alpha")
    }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun formatReleaseTime(releaseTime: String): String {
    return try {
        // Parse ISO 8601 date and format nicely
        val instant = java.time.Instant.parse(releaseTime)
        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
        localDate.format(formatter)
    } catch (e: Exception) {
        releaseTime.substringBefore("T")
    }
}
