package com.mclauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mclauncher.viewmodels.ConsoleViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConsoleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(uiState.logs.size) {
        if (uiState.autoScroll && uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleAutoScroll() }) {
                        Icon(
                            if (uiState.autoScroll) Icons.Filled.VerticalAlignBottom 
                            else Icons.Outlined.VerticalAlignBottom,
                            contentDescription = "Auto-scroll",
                            tint = if (uiState.autoScroll) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.copyLogs() }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.showInfo,
                    onClick = { viewModel.toggleFilter(LogLevel.INFO) },
                    label = { Text("Info") },
                    leadingIcon = {
                        if (uiState.showInfo) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                FilterChip(
                    selected = uiState.showWarning,
                    onClick = { viewModel.toggleFilter(LogLevel.WARNING) },
                    label = { Text("Warning") },
                    leadingIcon = {
                        if (uiState.showWarning) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                FilterChip(
                    selected = uiState.showError,
                    onClick = { viewModel.toggleFilter(LogLevel.ERROR) },
                    label = { Text("Error") },
                    leadingIcon = {
                        if (uiState.showError) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                FilterChip(
                    selected = uiState.showDebug,
                    onClick = { viewModel.toggleFilter(LogLevel.DEBUG) },
                    label = { Text("Debug") },
                    leadingIcon = {
                        if (uiState.showDebug) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            // Console output
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
            ) {
                if (uiState.filteredLogs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.logs.isEmpty()) 
                                "No logs yet" 
                            else 
                                "No logs match the current filter",
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(uiState.filteredLogs) { logEntry ->
                            LogEntryItem(logEntry)
                        }
                    }
                }

                // Scroll to bottom FAB
                if (!uiState.autoScroll && uiState.filteredLogs.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(uiState.filteredLogs.size - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.INFO -> Color(0xFF4FC3F7)
        LogLevel.WARNING -> Color(0xFFFFB74D)
        LogLevel.ERROR -> Color(0xFFEF5350)
        LogLevel.DEBUG -> Color(0xFF81C784)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "[${entry.timestamp}]",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "[${entry.level.name}]",
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = entry.message,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

enum class LogLevel {
    INFO, WARNING, ERROR, DEBUG
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)
