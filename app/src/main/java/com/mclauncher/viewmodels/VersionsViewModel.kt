package com.mclauncher.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.core.download.VersionDownloader
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.repository.VersionRepository
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.domain.models.VersionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class VersionsUiState(
    val versions: List<GameVersion> = emptyList(),
    val filteredVersions: List<GameVersion> = emptyList(),
    val installedVersions: List<GameVersion> = emptyList(),
    val latestRelease: GameVersion? = null,
    val latestSnapshot: GameVersion? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val showReleases: Boolean = true,
    val showSnapshots: Boolean = false,
    val showOldBeta: Boolean = false,
    val showOldAlpha: Boolean = false,
    val showInstalledOnly: Boolean = false,
    val downloadingVersions: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class VersionsViewModel @Inject constructor(
    private val versionRepository: VersionRepository,
    private val versionDownloader: VersionDownloader,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VersionsUiState())
    val uiState: StateFlow<VersionsUiState> = _uiState.asStateFlow()

    private var downloadJobs = mutableMapOf<String, Job>()

    init {
        loadVersions()
        observePreferences()
    }

    private fun loadVersions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Fetch from network first
            versionRepository.fetchVersionManifest()

            // Observe all versions
            launch {
                versionRepository.getAllVersions().collect { versions ->
                    _uiState.update { state ->
                        state.copy(
                            versions = versions,
                            filteredVersions = filterVersions(versions, state)
                        )
                    }
                }
            }

            // Observe installed versions
            launch {
                versionRepository.getInstalledVersions().collect { installed ->
                    _uiState.update { it.copy(installedVersions = installed) }
                }
            }

            // Get latest versions
            val latestRelease = versionRepository.getLatestRelease()
            val latestSnapshot = versionRepository.getLatestSnapshot()

            _uiState.update { 
                it.copy(
                    latestRelease = latestRelease,
                    latestSnapshot = latestSnapshot,
                    isLoading = false
                )
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesManager.preferences.collect { prefs ->
                _uiState.update { state ->
                    val newState = state.copy(
                        showReleases = prefs.showReleases,
                        showSnapshots = prefs.showSnapshots,
                        showOldBeta = prefs.showOldBeta,
                        showOldAlpha = prefs.showOldAlpha
                    )
                    newState.copy(filteredVersions = filterVersions(state.versions, newState))
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            versionRepository.fetchVersionManifest()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun search(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredVersions = filterVersions(state.versions, state.copy(searchQuery = query))
            )
        }
    }

    fun setFilter(
        showReleases: Boolean = _uiState.value.showReleases,
        showSnapshots: Boolean = _uiState.value.showSnapshots,
        showOldBeta: Boolean = _uiState.value.showOldBeta,
        showOldAlpha: Boolean = _uiState.value.showOldAlpha,
        showInstalledOnly: Boolean = _uiState.value.showInstalledOnly
    ) {
        viewModelScope.launch {
            preferencesManager.setVersionFilters(showReleases, showSnapshots, showOldBeta, showOldAlpha)
            
            _uiState.update { state ->
                val newState = state.copy(
                    showReleases = showReleases,
                    showSnapshots = showSnapshots,
                    showOldBeta = showOldBeta,
                    showOldAlpha = showOldAlpha,
                    showInstalledOnly = showInstalledOnly
                )
                newState.copy(filteredVersions = filterVersions(state.versions, newState))
            }
        }
    }

    private fun filterVersions(versions: List<GameVersion>, state: VersionsUiState): List<GameVersion> {
        return versions.filter { version ->
            // Filter by type
            val typeMatch = when (version.type) {
                VersionType.RELEASE -> state.showReleases
                VersionType.SNAPSHOT -> state.showSnapshots
                VersionType.OLD_BETA -> state.showOldBeta
                VersionType.OLD_ALPHA -> state.showOldAlpha
            }

            // Filter by installed
            val installedMatch = if (state.showInstalledOnly) version.isInstalled else true

            // Filter by search query
            val searchMatch = if (state.searchQuery.isNotBlank()) {
                version.id.contains(state.searchQuery, ignoreCase = true)
            } else true

            typeMatch && installedMatch && searchMatch
        }
    }

    fun downloadVersion(versionId: String) {
        if (_uiState.value.downloadingVersions.contains(versionId)) {
            Timber.d("Version $versionId is already downloading")
            return
        }

        val job = viewModelScope.launch {
            _uiState.update { 
                it.copy(downloadingVersions = it.downloadingVersions + versionId)
            }

            versionDownloader.downloadVersion(versionId).collect { state ->
                when (state) {
                    is VersionDownloader.DownloadState.Downloading -> {
                        _uiState.update { 
                            it.copy(
                                downloadProgress = it.downloadProgress + (versionId to state.progress)
                            )
                        }
                    }
                    is VersionDownloader.DownloadState.Completed -> {
                        _uiState.update { 
                            it.copy(
                                downloadingVersions = it.downloadingVersions - versionId,
                                downloadProgress = it.downloadProgress - versionId
                            )
                        }
                        downloadJobs.remove(versionId)
                    }
                    is VersionDownloader.DownloadState.Failed -> {
                        _uiState.update { 
                            it.copy(
                                downloadingVersions = it.downloadingVersions - versionId,
                                downloadProgress = it.downloadProgress - versionId,
                                error = "Failed to download $versionId: ${state.error}"
                            )
                        }
                        downloadJobs.remove(versionId)
                    }
                    else -> {}
                }
            }
        }

        downloadJobs[versionId] = job
    }

    fun cancelDownload(versionId: String) {
        downloadJobs[versionId]?.cancel()
        downloadJobs.remove(versionId)
        _uiState.update { 
            it.copy(
                downloadingVersions = it.downloadingVersions - versionId,
                downloadProgress = it.downloadProgress - versionId
            )
        }
    }

    fun deleteVersion(versionId: String) {
        viewModelScope.launch {
            try {
                versionDownloader.deleteVersion(versionId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete version: ${e.message}") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        downloadJobs.values.forEach { it.cancel() }
    }
}
