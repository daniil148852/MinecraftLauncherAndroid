package com.mclauncher.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.MCLauncherApp
import com.mclauncher.core.runtime.JREManager
import com.mclauncher.data.local.preferences.AppPreferences
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.utils.Constants
import com.mclauncher.utils.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val isLoading: Boolean = true,
    val javaInstalled: Boolean = false,
    val javaVersion: String = "Not installed",
    val gameDirectorySize: String = "Calculating...",
    val cacheSize: String = "Calculating...",
    val showClearCacheDialog: Boolean = false,
    val showResetSettingsDialog: Boolean = false,
    val showJavaDownloadDialog: Boolean = false,
    val javaDownloadProgress: Float = 0f,
    val javaDownloadStatus: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val jreManager: JREManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        checkJavaInstallation()
        calculateStorageUsage()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            preferencesManager.preferences.collect { prefs ->
                _uiState.update { 
                    it.copy(
                        preferences = prefs,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun checkJavaInstallation() {
        viewModelScope.launch {
            val installed = jreManager.isJavaInstalled()
            val version = if (installed) jreManager.getJavaVersion() else "Not installed"
            
            _uiState.update { 
                it.copy(
                    javaInstalled = installed,
                    javaVersion = version
                )
            }
        }
    }

    private fun calculateStorageUsage() {
        viewModelScope.launch {
            val gameSize = FileUtils.getDirectorySize(MCLauncherApp.gameDirectory)
            val cacheSize = FileUtils.getDirectorySize(MCLauncherApp.instance.cacheDir)
            
            _uiState.update { 
                it.copy(
                    gameDirectorySize = FileUtils.formatFileSize(gameSize),
                    cacheSize = FileUtils.formatFileSize(cacheSize)
                )
            }
        }
    }

    // RAM Settings
    fun setDefaultRam(ramMb: Int) {
        viewModelScope.launch {
            val clampedRam = ramMb.coerceIn(Constants.MIN_RAM_MB, Constants.MAX_RAM_MB)
            preferencesManager.updatePreference(PreferencesManager.DEFAULT_RAM_MB, clampedRam)
        }
    }

    // Resolution Settings
    fun setDefaultResolution(width: Int, height: Int) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.DEFAULT_WIDTH, width)
            preferencesManager.updatePreference(PreferencesManager.DEFAULT_HEIGHT, height)
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.DEFAULT_FULLSCREEN, fullscreen)
        }
    }

    // Version Filter Settings
    fun setShowReleases(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.SHOW_RELEASES, show)
        }
    }

    fun setShowSnapshots(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.SHOW_SNAPSHOTS, show)
        }
    }

    fun setShowOldBeta(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.SHOW_OLD_BETA, show)
        }
    }

    fun setShowOldAlpha(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.SHOW_OLD_ALPHA, show)
        }
    }

    // Download Settings
    fun setMaxConcurrentDownloads(count: Int) {
        viewModelScope.launch {
            val clamped = count.coerceIn(1, 8)
            preferencesManager.updatePreference(PreferencesManager.MAX_CONCURRENT_DOWNLOADS, clamped)
        }
    }

    fun setVerifyChecksums(verify: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.DOWNLOAD_VERIFY_CHECKSUMS, verify)
        }
    }

    // UI Settings
    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.DARK_MODE, mode)
        }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.DYNAMIC_COLORS, enabled)
        }
    }

    // Control Settings
    fun setControlScale(scale: Float) {
        viewModelScope.launch {
            val clamped = scale.coerceIn(0.5f, 2.0f)
            preferencesManager.updatePreference(PreferencesManager.CONTROL_SCALE, clamped)
        }
    }

    fun setControlOpacity(opacity: Float) {
        viewModelScope.launch {
            val clamped = opacity.coerceIn(0.1f, 1.0f)
            preferencesManager.updatePreference(PreferencesManager.CONTROL_OPACITY, clamped)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.VIBRATION_ENABLED, enabled)
        }
    }

    // Performance Settings
    fun setRenderer(renderer: String) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.RENDERER, renderer)
        }
    }

    // JVM Arguments
    fun setCustomJvmArgs(args: String) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.CUSTOM_JVM_ARGS, args)
        }
    }

    // Misc Settings
    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.KEEP_SCREEN_ON, enabled)
        }
    }

    fun setShowConsole(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.updatePreference(PreferencesManager.SHOW_CONSOLE, show)
        }
    }

    // Java Runtime
    fun showJavaDownloadDialog() {
        _uiState.update { it.copy(showJavaDownloadDialog = true) }
    }

    fun hideJavaDownloadDialog() {
        _uiState.update { 
            it.copy(
                showJavaDownloadDialog = false,
                javaDownloadProgress = 0f,
                javaDownloadStatus = ""
            )
        }
    }

    fun downloadJava() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    javaDownloadProgress = 0f,
                    javaDownloadStatus = "Downloading Java Runtime..."
                )
            }

            jreManager.downloadJRE().collect { state ->
                when (state) {
                    is JREManager.DownloadState.Downloading -> {
                        _uiState.update { 
                            it.copy(
                                javaDownloadProgress = state.progress,
                                javaDownloadStatus = "Downloading: ${(state.progress * 100).toInt()}%"
                            )
                        }
                    }
                    is JREManager.DownloadState.Extracting -> {
                        _uiState.update { 
                            it.copy(javaDownloadStatus = "Extracting...")
                        }
                    }
                    is JREManager.DownloadState.Completed -> {
                        checkJavaInstallation()
                        _uiState.update { 
                            it.copy(
                                showJavaDownloadDialog = false,
                                successMessage = "Java Runtime installed successfully"
                            )
                        }
                    }
                    is JREManager.DownloadState.Failed -> {
                        _uiState.update { 
                            it.copy(
                                showJavaDownloadDialog = false,
                                error = "Failed to download Java: ${state.error}"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    // Cache Management
    fun showClearCacheDialog() {
        _uiState.update { it.copy(showClearCacheDialog = true) }
    }

    fun hideClearCacheDialog() {
        _uiState.update { it.copy(showClearCacheDialog = false) }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                FileUtils.deleteRecursively(MCLauncherApp.instance.cacheDir)
                MCLauncherApp.instance.cacheDir.mkdirs()
                calculateStorageUsage()
                _uiState.update { 
                    it.copy(
                        showClearCacheDialog = false,
                        successMessage = "Cache cleared successfully"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        showClearCacheDialog = false,
                        error = "Failed to clear cache: ${e.message}"
                    )
                }
            }
        }
    }

    // Reset Settings
    fun showResetSettingsDialog() {
        _uiState.update { it.copy(showResetSettingsDialog = true) }
    }

    fun hideResetSettingsDialog() {
        _uiState.update { it.copy(showResetSettingsDialog = false) }
    }

    fun resetSettings() {
        viewModelScope.launch {
            try {
                preferencesManager.clearAllPreferences()
                _uiState.update { 
                    it.copy(
                        showResetSettingsDialog = false,
                        successMessage = "Settings reset to defaults"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        showResetSettingsDialog = false,
                        error = "Failed to reset settings: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
