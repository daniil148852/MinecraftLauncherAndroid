package com.mclauncher.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.core.download.VersionDownloader
import com.mclauncher.core.launcher.GameLauncher
import com.mclauncher.core.launcher.LaunchState
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.repository.AccountRepository
import com.mclauncher.data.repository.ProfileRepository
import com.mclauncher.data.repository.VersionRepository
import com.mclauncher.domain.models.Account
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.domain.models.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class HomeUiState(
    val selectedProfile: Profile? = null,
    val activeAccount: Account? = null,
    val recentProfiles: List<Profile> = emptyList(),
    val installedVersions: List<GameVersion> = emptyList(),
    val isLoading: Boolean = true,
    val isLaunching: Boolean = false,
    val launchProgress: Float = 0f,
    val launchStatus: String = "",
    val error: String? = null,
    val canLaunch: Boolean = false,
    val showDownloadDialog: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    private val versionRepository: VersionRepository,
    private val versionDownloader: VersionDownloader,
    private val gameLauncher: GameLauncher,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observePreferences()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Observe active account
            launch {
                accountRepository.getActiveAccountFlow().collect { account ->
                    _uiState.update { state ->
                        state.copy(
                            activeAccount = account,
                            canLaunch = account != null && state.selectedProfile != null
                        )
                    }
                }
            }

            // Observe profiles
            launch {
                profileRepository.getMostPlayedProfiles(5).collect { profiles ->
                    _uiState.update { it.copy(recentProfiles = profiles) }
                }
            }

            // Observe installed versions
            launch {
                versionRepository.getInstalledVersions().collect { versions ->
                    _uiState.update { it.copy(installedVersions = versions) }
                }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesManager.preferences.collect { prefs ->
                prefs.selectedProfileId?.let { profileId ->
                    val profile = profileRepository.getProfileById(profileId)
                    _uiState.update { state ->
                        state.copy(
                            selectedProfile = profile,
                            canLaunch = state.activeAccount != null && profile != null
                        )
                    }
                }
            }
        }
    }

    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            preferencesManager.setSelectedProfile(profile.id)
            _uiState.update { state ->
                state.copy(
                    selectedProfile = profile,
                    canLaunch = state.activeAccount != null
                )
            }
        }
    }

    fun launchGame() {
        val state = _uiState.value
        val profile = state.selectedProfile ?: return
        val account = state.activeAccount ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLaunching = true, error = null) }

            // Check if version is installed
            val version = versionRepository.getVersionById(profile.versionId)
            if (version == null || !version.isInstalled) {
                // Need to download first
                _uiState.update { 
                    it.copy(
                        showDownloadDialog = true,
                        downloadStatus = "Preparing to download ${profile.versionId}..."
                    )
                }
                downloadAndLaunch(profile, account)
                return@launch
            }

            // Launch directly
            launchWithVersion(profile, account)
        }
    }

    private suspend fun downloadAndLaunch(profile: Profile, account: Account) {
        versionDownloader.downloadVersion(profile.versionId).collect { state ->
            when (state) {
                is VersionDownloader.DownloadState.FetchingManifest -> {
                    _uiState.update { 
                        it.copy(downloadStatus = state.message)
                    }
                }
                is VersionDownloader.DownloadState.PreparingDownloads -> {
                    _uiState.update { 
                        it.copy(downloadStatus = state.message)
                    }
                }
                is VersionDownloader.DownloadState.Downloading -> {
                    _uiState.update { 
                        it.copy(
                            downloadProgress = state.progress,
                            downloadStatus = "Downloading: ${state.currentFile} (${state.downloaded}/${state.total})"
                        )
                    }
                }
                is VersionDownloader.DownloadState.Extracting -> {
                    _uiState.update { 
                        it.copy(downloadStatus = state.message)
                    }
                }
                is VersionDownloader.DownloadState.Completed -> {
                    _uiState.update { 
                        it.copy(
                            showDownloadDialog = false,
                            downloadProgress = 0f
                        )
                    }
                    launchWithVersion(profile, account)
                }
                is VersionDownloader.DownloadState.Failed -> {
                    _uiState.update { 
                        it.copy(
                            isLaunching = false,
                            showDownloadDialog = false,
                            error = state.error
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun launchWithVersion(profile: Profile, account: Account) {
        gameLauncher.launch(profile, account).collect { launchState ->
            when (launchState) {
                is LaunchState.Preparing -> {
                    _uiState.update { 
                        it.copy(
                            launchProgress = launchState.progress,
                            launchStatus = launchState.message
                        )
                    }
                }
                is LaunchState.Launching -> {
                    _uiState.update { 
                        it.copy(launchStatus = "Launching Minecraft...")
                    }
                }
                is LaunchState.Running -> {
                    _uiState.update { 
                        it.copy(
                            isLaunching = false,
                            launchStatus = "Game is running"
                        )
                    }
                    // Update last played
                    profileRepository.updateLastPlayed(profile.id)
                }
                is LaunchState.Stopped -> {
                    _uiState.update { 
                        it.copy(
                            isLaunching = false,
                            launchStatus = ""
                        )
                    }
                    // Update play time
                    profileRepository.addPlayTime(profile.id, launchState.playTimeMs)
                }
                is LaunchState.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLaunching = false,
                            error = launchState.message
                        )
                    }
                }
            }
        }
    }

    fun cancelLaunch() {
        gameLauncher.cancel()
        _uiState.update { 
            it.copy(
                isLaunching = false,
                showDownloadDialog = false,
                launchProgress = 0f,
                downloadProgress = 0f
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshVersions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            versionRepository.fetchVersionManifest()
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
