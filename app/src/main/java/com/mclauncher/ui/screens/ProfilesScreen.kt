package com.mclauncher.ui.screens

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
import com.mclauncher.domain.models.Profile
import com.mclauncher.ui.components.ProfileListItem
import com.mclauncher.viewmodels.ProfilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateBack: () -> Unit,
    onProfileClick: (String) -> Unit,
    onCreateProfile: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCreateProfile) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProfile,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Profile") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            } else if (uiState.profiles.isEmpty()) {
                EmptyProfilesState(onCreateProfile = onCreateProfile)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "${uiState.profiles.size} profile${if (uiState.profiles.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(
                        items = uiState.profiles,
                        key = { it.id }
                    ) { profile ->
                        ProfileListItem(
                            profile = profile,
                            isSelected = profile.id == uiState.selectedProfileId,
                            onClick = { viewModel.selectProfile(profile.id) },
                            onEdit = { onProfileClick(profile.id) },
                            onDuplicate = { viewModel.duplicateProfile(profile) },
                            onDelete = { viewModel.showDeleteConfirmation(profile) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    uiState.showDeleteConfirmation?.let { profile ->
        DeleteProfileDialog(
            profile = profile,
            onConfirm = { viewModel.deleteProfile(profile) },
            onDismiss = { viewModel.dismissDeleteConfirmation() }
        )
    }
}

@Composable
private fun EmptyProfilesState(onCreateProfile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.FolderCopy,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Profiles",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create a profile to customize your Minecraft experience",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onCreateProfile) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Profile")
        }
    }
}

@Composable
private fun DeleteProfileDialog(
    profile: Profile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Profile?") },
        text = {
            Text("Are you sure you want to delete \"${profile.name}\"? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
