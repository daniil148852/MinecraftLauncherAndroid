package com.mclauncher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.mclauncher.utils.Constants
import com.mclauncher.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Java Runtime Section
            SettingsSection(title = "Java Runtime") {
                SettingsItem(
                    icon = Icons.Outlined.Code,
                    title = "Java Version",
                    subtitle = uiState.javaVersion,
                    onClick = {
                        if (!uiState.javaInstalled) {
                            viewModel.showJavaDownloadDialog()
                        }
                    },
                    trailing = {
                        if (uiState.javaInstalled) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Installed",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = { viewModel.showJavaDownloadDialog() }) {
                                Text("Install")
                            }
                        }
                    }
                )
            }

            // Game Defaults Section
            SettingsSection(title = "Game Defaults") {
                SettingsSliderItem(
                    icon = Icons.Outlined.Memory,
                    title = "Default RAM",
                    value = uiState.preferences.defaultRamMb,
                    valueText = "${uiState.preferences.defaultRamMb} MB",
                    valueRange = Constants.MIN_RAM_MB.toFloat()..Constants.MAX_RAM_MB.toFloat(),
                    steps = ((Constants.MAX_RAM_MB - Constants.MIN_RAM_MB) / 256) - 1,
                    onValueChange = { viewModel.setDefaultRam(it.toInt()) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.Fullscreen,
                    title = "Default Fullscreen",
                    subtitle = "Launch games in fullscreen by default",
                    checked = uiState.preferences.defaultFullscreen,
                    onCheckedChange = viewModel::setFullscreen
                )
            }

            // Version Filters Section
            SettingsSection(title = "Version Filters") {
                SettingsSwitchItem(
                    icon = Icons.Outlined.NewReleases,
                    title = "Show Releases",
                    subtitle = "Show stable release versions",
                    checked = uiState.preferences.showReleases,
                    onCheckedChange = viewModel::setShowReleases
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.Science,
                    title = "Show Snapshots",
                    subtitle = "Show development snapshots",
                    checked = uiState.preferences.showSnapshots,
                    onCheckedChange = viewModel::setShowSnapshots
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.History,
                    title = "Show Old Beta",
                    subtitle = "Show beta versions (2010-2011)",
                    checked = uiState.preferences.showOldBeta,
                    onCheckedChange = viewModel::setShowOldBeta
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.HistoryToggleOff,
                    title = "Show Old Alpha",
                    subtitle = "Show alpha versions (2010)",
                    checked = uiState.preferences.showOldAlpha,
                    onCheckedChange = viewModel::setShowOldAlpha
                )
            }

            // Controls Section
            SettingsSection(title = "Controls") {
                SettingsSliderItem(
                    icon = Icons.Outlined.ZoomIn,
                    title = "Control Scale",
                    value = uiState.preferences.controlScale,
                    valueText = "${(uiState.preferences.controlScale * 100).toInt()}%",
                    valueRange = 0.5f..2.0f,
                    onValueChange = viewModel::setControlScale
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSliderItem(
                    icon = Icons.Outlined.Opacity,
                    title = "Control Opacity",
                    value = uiState.preferences.controlOpacity,
                    valueText = "${(uiState.preferences.controlOpacity * 100).toInt()}%",
                    valueRange = 0.1f..1.0f,
                    onValueChange = viewModel::setControlOpacity
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.Vibration,
                    title = "Vibration",
                    subtitle = "Haptic feedback for controls",
                    checked = uiState.preferences.vibrationEnabled,
                    onCheckedChange = viewModel::setVibrationEnabled
                )
            }

            // Download Section
            SettingsSection(title = "Downloads") {
                SettingsSliderItem(
                    icon = Icons.Outlined.Speed,
                    title = "Concurrent Downloads",
                    value = uiState.preferences.maxConcurrentDownloads.toFloat(),
                    valueText = uiState.preferences.maxConcurrentDownloads.toString(),
                    valueRange = 1f..8f,
                    steps = 6,
                    onValueChange = { viewModel.setMaxConcurrentDownloads(it.toInt()) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "Verify Downloads",
                    subtitle = "Check file integrity after download",
                    checked = uiState.preferences.downloadVerifyChecksums,
                    onCheckedChange = viewModel::setVerifyChecksums
                )
            }

            // Appearance Section
            SettingsSection(title = "Appearance") {
                SettingsDropdownItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Theme",
                    selectedValue = uiState.preferences.darkMode,
                    options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                    onValueChange = viewModel::setDarkMode
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.Palette,
                    title = "Dynamic Colors",
                    subtitle = "Use Material You colors (Android 12+)",
                    checked = uiState.preferences.dynamicColors,
                    onCheckedChange = viewModel::setDynamicColors
                )
            }

            // Storage Section
            SettingsSection(title = "Storage") {
                SettingsItem(
                    icon = Icons.Outlined.Folder,
                    title = "Game Data",
                    subtitle = uiState.gameDirectorySize,
                    onClick = {}
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsItem(
                    icon = Icons.Outlined.CleaningServices,
                    title = "Clear Cache",
                    subtitle = uiState.cacheSize,
                    onClick = { viewModel.showClearCacheDialog() }
                )
            }

            // Misc Section
            SettingsSection(title = "Miscellaneous") {
                SettingsSwitchItem(
                    icon = Icons.Outlined.ScreenLockPortrait,
                    title = "Keep Screen On",
                    subtitle = "Prevent screen from sleeping during gameplay",
                    checked = uiState.preferences.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    icon = Icons.Outlined.Terminal,
                    title = "Show Console",
                    subtitle = "Display game output console",
                    checked = uiState.preferences.showConsole,
                    onCheckedChange = viewModel::setShowConsole
                )
            }

            // Danger Zone
            SettingsSection(title = "Danger Zone") {
                SettingsItem(
                    icon = Icons.Outlined.RestartAlt,
                    title = "Reset Settings",
                    subtitle = "Restore all settings to defaults",
                    onClick = { viewModel.showResetSettingsDialog() },
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Java Download Dialog
    if (uiState.showJavaDownloadDialog) {
        JavaDownloadDialog(
            progress = uiState.javaDownloadProgress,
            status = uiState.javaDownloadStatus,
            onDownload = viewModel::downloadJava,
            onDismiss = viewModel::hideJavaDownloadDialog
        )
    }

    // Clear Cache Dialog
    if (uiState.showClearCacheDialog) {
        ConfirmationDialog(
            title = "Clear Cache?",
            message = "This will delete cached files. Downloaded versions and profiles will not be affected.",
            confirmText = "Clear",
            onConfirm = viewModel::clearCache,
            onDismiss = viewModel::hideClearCacheDialog
        )
    }

    // Reset Settings Dialog
    if (uiState.showResetSettingsDialog) {
        ConfirmationDialog(
            title = "Reset Settings?",
            message = "This will restore all settings to their default values. Your profiles and downloaded versions will not be affected.",
            confirmText = "Reset",
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = viewModel::resetSettings,
            onDismiss = viewModel::hideResetSettingsDialog
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { 
            Text(
                text = title,
                color = tint
            )
        },
        supportingContent = { 
            Text(
                text = subtitle,
                color = if (tint != MaterialTheme.colorScheme.onSurface)
                    tint.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint
            )
        },
        trailingContent = trailing,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsSliderItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Text(text = title)
            }
            Text(
                text = valueText,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedValue }?.second ?: selectedValue

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(selectedLabel) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.menuAnchor()
                ) {
                    Text(selectedLabel)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onValueChange(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun JavaDownloadDialog(
    progress: Float,
    status: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (progress == 0f) onDismiss() },
        title = { Text("Java Runtime") },
        text = {
            Column {
                if (progress > 0f) {
                    Text(status)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Java Runtime Environment is required to run Minecraft. Would you like to download it now?")
                }
            }
        },
        confirmButton = {
            if (progress == 0f) {
                Button(onClick = onDownload) {
                    Text("Download")
                }
            }
        },
        dismissButton = {
            if (progress == 0f) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
