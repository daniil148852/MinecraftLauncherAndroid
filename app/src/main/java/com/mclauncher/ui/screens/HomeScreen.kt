package com.mclauncher.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mclauncher.domain.models.Profile
import com.mclauncher.ui.components.ProfileCard
import com.mclauncher.ui.theme.MCColors
import com.mclauncher.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToVersions: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToConsole: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "MC Launcher",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Main Launch Card
                    LaunchCard(
                        selectedProfile = uiState.selectedProfile,
                        activeAccount = uiState.activeAccount,
                        isLaunching = uiState.isLaunching,
                        launchProgress = uiState.launchProgress,
                        launchStatus = uiState.launchStatus,
                        canLaunch = uiState.canLaunch,
                        onLaunch = viewModel::launchGame,
                        onCancel = viewModel::cancelLaunch,
                        onSelectAccount = onNavigateToAccounts,
                        onSelectProfile = onNavigateToProfiles
                    )

                    // Quick Actions
                    QuickActionsRow(
                        onVersionsClick = onNavigateToVersions,
                        onProfilesClick = onNavigateToProfiles,
                        onAccountsClick = onNavigateToAccounts,
                        onConsoleClick = onNavigateToConsole
                    )

                    // Recent Profiles
                    if (uiState.recentProfiles.isNotEmpty()) {
                        RecentProfilesSection(
                            profiles = uiState.recentProfiles,
                            selectedProfileId = uiState.selectedProfile?.id,
                            onProfileClick = { profile ->
                                viewModel.selectProfile(profile)
                            },
                            onProfileLongClick = { profile ->
                                onNavigateToProfile(profile.id)
                            },
                            onViewAll = onNavigateToProfiles
                        )
                    }

                    // Installed Versions Summary
                    if (uiState.installedVersions.isNotEmpty()) {
                        InstalledVersionsCard(
                            count = uiState.installedVersions.size,
                            onViewAll = onNavigateToVersions
                        )
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Download Dialog
            if (uiState.showDownloadDialog) {
                DownloadProgressDialog(
                    progress = uiState.downloadProgress,
                    status = uiState.downloadStatus,
                    onCancel = viewModel::cancelLaunch
                )
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
}

@Composable
private fun LaunchCard(
    selectedProfile: Profile?,
    activeAccount: com.mclauncher.domain.models.Account?,
    isLaunching: Boolean,
    launchProgress: Float,
    launchStatus: String,
    canLaunch: Boolean,
    onLaunch: () -> Unit,
    onCancel: () -> Unit,
    onSelectAccount: () -> Unit,
    onSelectProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedProfile?.name ?: "No Profile Selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (selectedProfile != null) {
                        Text(
                            text = selectedProfile.displayVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                IconButton(onClick = onSelectProfile) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Change Profile",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Account Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = activeAccount?.username ?: "No Account",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAccount) {
                    Text("Change")
                }
            }

            // Launch Progress
            AnimatedVisibility(visible = isLaunching) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { launchProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = launchStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Launch Button
            Button(
                onClick = if (isLaunching) onCancel else onLaunch,
                enabled = canLaunch || isLaunching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLaunching) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isLaunching) Icons.Filled.Close else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLaunching) "Cancel" else "PLAY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Warning if can't launch
            if (!canLaunch && !isLaunching) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            activeAccount == null -> "Please add an account first"
                            selectedProfile == null -> "Please select a profile"
                            else -> "Unable to launch"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onVersionsClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onConsoleClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Outlined.Inventory2,
            label = "Versions",
            onClick = onVersionsClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Outlined.FolderCopy,
            label = "Profiles",
            onClick = onProfilesClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Outlined.AccountCircle,
            label = "Accounts",
            onClick = onAccountsClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Outlined.Terminal,
            label = "Console",
            onClick = onConsoleClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun RecentProfilesSection(
    profiles: List<Profile>,
    selectedProfileId: String?,
    onProfileClick: (Profile) -> Unit,
    onProfileLongClick: (Profile) -> Unit,
    onViewAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Profiles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onViewAll) {
                Text("View All")
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(profiles) { profile ->
                ProfileCard(
                    profile = profile,
                    isSelected = profile.id == selectedProfileId,
                    onClick = { onProfileClick(profile) },
                    onLongClick = { onProfileLongClick(profile) },
                    modifier = Modifier.width(200.dp)
                )
            }
        }
    }
}

@Composable
private fun InstalledVersionsCard(
    count: Int,
    onViewAll: () -> Unit
) {
    OutlinedCard(
        onClick = onViewAll,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "$count Versions Installed",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap to manage versions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadProgressDialog(
    progress: Float,
    status: String,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Cannot dismiss */ },
        title = { Text("Downloading") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
