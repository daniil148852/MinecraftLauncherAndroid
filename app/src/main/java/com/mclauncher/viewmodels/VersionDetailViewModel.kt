package com.mclauncher.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.core.download.VersionDownloader
import com.mclauncher.data.repository.VersionRepository
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.utils.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class VersionDetailUiState(
    val version: GameVersion? = null,
    val versionDetails: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = "",
    val installedSize: String? = null,
    val showDeleteDialog: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VersionDetailViewModel @Inject constructor(
    private val versionRepository: VersionRepository,
    private val versionDownloader: VersionDownloader
) : ViewModel() {

    private val _uiState = MutableStateFlow(VersionDetailUiState())
    val uiState: StateFlow<VersionDetailUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var currentVersionId: String? = null

    fun loadVersion(versionId: String) {
        currentVersionId = versionId
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Load version
                val version = versionRepository.getVersionById(versionId)
                
                if (version == null) {
                    // Try fetching from network
                    versionRepository.fetchVersionManifest()
                    val fetchedVersion = versionRepository.getVersionById(versionId)
                    
                    if (fetchedVersion == null) {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Version not found"
                            )
                        }
                        return@launch
                    }
                }

                // Observe version for updates
                versionRepository.getVersionByIdFlow(versionId).collect { version ->
                    if (version != null) {
                        val details = mutableMapOf<String, String>()
                        version.mainClass?.let { details["mainClass"] = it }
                        version.assetsId?.let { details["assetsId"] = it }

                        val size = if (version.isInstalled && version.installPath != null) {
                            val dir = File(version.installPath)
                            FileUtils.formatFileSize(FileUtils.getDirectorySize(dir))
                        } else null

                        _uiState.update {
                            it.copy(
                                version = version,
                                versionDetails = details,
                                installedSize = size,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load version")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load version: ${e.message}"
                    )
                }
            }
        }
    }

    fun downloadVersion() {
        val versionId = currentVersionId ?: return

        downloadJob = viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadStatus = "Preparing..."
                )
            }

            versionDownloader.downloadVersion(versionId).collect { state ->
                when (state) {
                    is VersionDownloader.DownloadState.FetchingManifest -> {
                        _uiState.update { it.copy(downloadStatus = state.message) }
                    }
                    is VersionDownloader.DownloadState.PreparingDownloads -> {
                        _uiState.update { it.copy(downloadStatus = state.message) }
                    }
                    is VersionDownloader.DownloadState.Downloading -> {
                        _uiState.update {
                            it.copy(
                                downloadProgress = state.progress,
                                downloadStatus = "Downloading: ${state.downloaded}/${state.total} files"
                            )
                        }
                    }
                    is VersionDownloader.DownloadState.Extracting -> {
                        _uiState.update { it.copy(downloadStatus = state.message) }
                    }
                    is VersionDownloader.DownloadState.Completed -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = 1f,
                                downloadStatus = ""
                            )
                        }
                        // Reload version to get updated state
                        loadVersion(versionId)
                    }
                    is VersionDownloader.DownloadState.Failed -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = 0f,
                                downloadStatus = "",
                                error = state.error
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update {
            it.copy(
                isDownloading = false,
                downloadProgress = 0f,
                downloadStatus = ""
            )
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteVersion() {
        val versionId = currentVersionId ?: return

        viewModelScope.launch {
            try {
                versionDownloader.deleteVersion(versionId)
                _uiState.update { it.copy(showDeleteDialog = false) }
                loadVersion(versionId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showDeleteDialog = false,
                        error = "Failed to delete version: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
    }
}
