package com.mclauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mclauncher.domain.models.Account
import com.mclauncher.domain.models.AccountType
import com.mclauncher.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSuccess()
        }
    }

    // Show error message
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::showAddOfflineDialog,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add Account") }
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
            } else if (uiState.accounts.isEmpty()) {
                EmptyAccountsState(
                    onAddOffline = viewModel::showAddOfflineDialog
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Select an account to use when playing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(
                        items = uiState.accounts,
                        key = { it.id }
                    ) { account ->
                        AccountCard(
                            account = account,
                            isActive = account.id == uiState.activeAccount?.id,
                            onSelect = { viewModel.setActiveAccount(account) },
                            onEdit = { viewModel.showEditUsernameDialog(account) },
                            onDelete = { viewModel.showDeleteConfirmation(account) },
                            onRefreshToken = { viewModel.refreshToken(account) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Add Offline Account Dialog
    if (uiState.showAddOfflineDialog) {
        AddOfflineAccountDialog(
            username = uiState.offlineUsername,
            usernameError = uiState.offlineUsernameError,
            isLoading = uiState.isAddingAccount,
            onUsernameChange = viewModel::updateOfflineUsername,
            onConfirm = viewModel::createOfflineAccount,
            onDismiss = viewModel::hideAddOfflineDialog
        )
    }

    // Edit Username Dialog
    uiState.showEditUsernameDialog?.let { account ->
        EditUsernameDialog(
            currentUsername = account.username,
            newUsername = uiState.editUsername,
            usernameError = uiState.editUsernameError,
            onUsernameChange = viewModel::updateEditUsername,
            onConfirm = viewModel::saveUsername,
            onDismiss = viewModel::hideEditUsernameDialog
        )
    }

    // Delete Confirmation Dialog
    uiState.showDeleteConfirmation?.let { account ->
        DeleteAccountDialog(
            account = account,
            onConfirm = viewModel::deleteAccount,
            onDismiss = viewModel::hideDeleteConfirmation
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountCard(
    account: Account,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefreshToken: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) null else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (account.accountType == AccountType.OFFLINE)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (account.skinUrl != null) {
                    AsyncImage(
                        model = account.skinUrl,
                        contentDescription = "Skin",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = account.username.first().uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (account.accountType == AccountType.OFFLINE)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = account.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (account.accountType == AccountType.OFFLINE)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (account.accountType == AccountType.OFFLINE) "Offline" else "Microsoft",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (account.accountType == AccountType.MICROSOFT && account.isExpired) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Expired",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Options"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (account.accountType == AccountType.OFFLINE) {
                        DropdownMenuItem(
                            text = { Text("Edit Username") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                            }
                        )
                    }
                    
                    if (account.accountType == AccountType.MICROSOFT) {
                        DropdownMenuItem(
                            text = { Text("Refresh Token") },
                            onClick = {
                                showMenu = false
                                onRefreshToken()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
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
                }
            }
        }
    }
}

@Composable
private fun EmptyAccountsState(
    onAddOffline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No Accounts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add an account to start playing Minecraft",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onAddOffline) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Offline Account")
        }
    }
}

@Composable
private fun AddOfflineAccountDialog(
    username: String,
    usernameError: String?,
    isLoading: Boolean,
    onUsernameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add Offline Account") },
        text = {
            Column {
                Text(
                    text = "Enter a username for offline play. This won't connect to Mojang servers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    placeholder = { Text("Steve") },
                    singleLine = true,
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it) } },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading && username.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditUsernameDialog(
    currentUsername: String,
    newUsername: String,
    usernameError: String?,
    onUsernameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Username") },
        text = {
            Column {
                Text(
                    text = "Current: $currentUsername",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = newUsername,
                    onValueChange = onUsernameChange,
                    label = { Text("New Username") },
                    singleLine = true,
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = newUsername.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteAccountDialog(
    account: Account,
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
        title = { Text("Delete Account?") },
        text = {
            Text("Are you sure you want to delete the account \"${account.username}\"? This action cannot be undone.")
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
