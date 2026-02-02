package com.mclauncher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.domain.models.ModLoader
import com.mclauncher.utils.Constants
import com.mclauncher.viewmodels.ProfilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profileId: String?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val editState by viewModel.editState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    LaunchedEffect(editState.error) {
        editState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (editState.isNew) "Create Profile" else "Edit Profile") 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (viewModel.saveProfile()) {
                                onSaved()
                            }
                        },
                        enabled = !editState.isSaving && editState.name.isNotBlank()
                    ) {
                        if (editState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (editState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Basic Info Section
                SectionCard(title = "Basic Information") {
                    OutlinedTextField(
                        value = editState.name,
                        onValueChange = { viewModel.updateEditState(name = it) },
                        label = { Text("Profile Name") },
                        placeholder = { Text("My Profile") },
                        singleLine = true,
                        isError = editState.nameError != null,
                        supportingText = editState.nameError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Version Section
                SectionCard(title = "Game Version") {
                    VersionSelector(
                        selectedVersionId = editState.versionId,
                        versions = editState.availableVersions,
                        onVersionSelected = { viewModel.updateEditState(versionId = it) }
                    )
                }

                // Mod Loader Section
                SectionCard(title = "Mod Loader") {
                    ModLoaderSelector(
                        selectedLoader = editState.modLoader,
                        selectedVersion = editState.modLoaderVersion,
                        onLoaderSelected = { loader, version ->
                            viewModel.updateEditState(modLoader = loader, modLoaderVersion = version)
                        }
                    )
                }

                // Memory Section
                SectionCard(title = "Memory") {
                    MemorySlider(
                        ramMb = editState.ramMb,
                        onRamChange = { viewModel.updateEditState(ramMb = it) }
                    )
                }

                // Resolution Section
                SectionCard(title = "Resolution") {
                    ResolutionSettings(
                        width = editState.width,
                        height = editState.height,
                        fullscreen = editState.fullscreen,
                        onWidthChange = { viewModel.updateEditState(width = it) },
                        onHeightChange = { viewModel.updateEditState(height = it) },
                        onFullscreenChange = { viewModel.updateEditState(fullscreen = it) }
                    )
                }

                // Advanced Section
                SectionCard(title = "Advanced") {
                    OutlinedTextField(
                        value = editState.jvmArguments,
                        onValueChange = { viewModel.updateEditState(jvmArguments = it) },
                        label = { Text("JVM Arguments") },
                        placeholder = { Text("One argument per line") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editState.gameArguments,
                        onValueChange = { viewModel.updateEditState(gameArguments = it) },
                        label = { Text("Game Arguments") },
                        placeholder = { Text("One argument per line") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSelector(
    selectedVersionId: String,
    versions: List<GameVersion>,
    onVersionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedVersion = versions.find { it.id == selectedVersionId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedVersion?.id ?: "Select version",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (versions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No versions installed") },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                versions.forEach { version ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(version.id)
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = version.type.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onVersionSelected(version.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModLoaderSelector(
    selectedLoader: ModLoader,
    selectedVersion: String?,
    onLoaderSelected: (ModLoader, String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ModLoader.entries.forEachIndexed { index, loader ->
                SegmentedButton(
                    selected = selectedLoader == loader,
                    onClick = { onLoaderSelected(loader, null) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ModLoader.entries.size
                    )
                ) {
                    Text(
                        text = loader.displayName,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (selectedLoader != ModLoader.NONE) {
            OutlinedTextField(
                value = selectedVersion ?: "",
                onValueChange = { onLoaderSelected(selectedLoader, it.ifBlank { null }) },
                label = { Text("${selectedLoader.displayName} Version") },
                placeholder = { Text("e.g., 0.15.6 or 47.2.0") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MemorySlider(
    ramMb: Int,
    onRamChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Allocated RAM",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${ramMb} MB",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = ramMb.toFloat(),
            onValueChange = { onRamChange(it.toInt()) },
            valueRange = Constants.MIN_RAM_MB.toFloat()..Constants.MAX_RAM_MB.toFloat(),
            steps = ((Constants.MAX_RAM_MB - Constants.MIN_RAM_MB) / 256) - 1,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${Constants.MIN_RAM_MB} MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${Constants.MAX_RAM_MB} MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResolutionSettings(
    width: Int,
    height: Int,
    fullscreen: Boolean,
    onWidthChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onFullscreenChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = width.toString(),
                onValueChange = { it.toIntOrNull()?.let(onWidthChange) },
                label = { Text("Width") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = height.toString(),
                onValueChange = { it.toIntOrNull()?.let(onHeightChange) },
                label = { Text("Height") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Fullscreen",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Launch game in fullscreen mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = fullscreen,
                onCheckedChange = onFullscreenChange
            )
        }
    }
}
