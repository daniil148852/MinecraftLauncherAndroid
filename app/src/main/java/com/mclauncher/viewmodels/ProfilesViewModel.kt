package com.mclauncher.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.repository.ProfileRepository
import com.mclauncher.data.repository.VersionRepository
import com.mclauncher.domain.models.GameSettings
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.domain.models.ModLoader
import com.mclauncher.domain.models.Profile
import com.mclauncher.domain.models.VersionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val availableVersions: List<GameVersion> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteConfirmation: Profile? = null
)

data class ProfileEditState(
    val profile: Profile? = null,
    val isNew: Boolean = true,
    val name: String = "",
    val versionId: String = "",
    val versionType: VersionType = VersionType.RELEASE,
    val modLoader: ModLoader = ModLoader.NONE,
    val modLoaderVersion: String? = null,
    val ramMb: Int = 2048,
    val width: Int = 1280,
    val height: Int = 720,
    val fullscreen: Boolean = false,
    val jvmArguments: String = "",
    val gameArguments: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val availableVersions: List<GameVersion> = emptyList(),
    val availableModLoaderVersions: List<String> = emptyList()
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val versionRepository: VersionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    private val _editState = MutableStateFlow(ProfileEditState())
    val editState: StateFlow<ProfileEditState> = _editState.asStateFlow()

    init {
        loadProfiles()
        loadVersions()
        observePreferences()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            profileRepository.getAllProfiles().collect { profiles ->
                _uiState.update { 
                    it.copy(
                        profiles = profiles,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadVersions() {
        viewModelScope.launch {
            versionRepository.getInstalledVersions().collect { versions ->
                _uiState.update { it.copy(availableVersions = versions) }
                _editState.update { it.copy(availableVersions = versions) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesManager.preferences.collect { prefs ->
                _uiState.update { it.copy(selectedProfileId = prefs.selectedProfileId) }
            }
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.setSelectedProfile(profileId)
        }
    }

    fun loadProfile(profileId: String?) {
        viewModelScope.launch {
            _editState.update { it.copy(isLoading = true) }

            if (profileId == null) {
                // New profile
                val latestVersion = versionRepository.getLatestRelease()
                _editState.update {
                    ProfileEditState(
                        isNew = true,
                        versionId = latestVersion?.id ?: "",
                        versionType = VersionType.RELEASE,
                        availableVersions = _uiState.value.availableVersions
                    )
                }
            } else {
                // Edit existing profile
                val profile = profileRepository.getProfileById(profileId)
                if (profile != null) {
                    _editState.update {
                        ProfileEditState(
                            profile = profile,
                            isNew = false,
                            name = profile.name,
                            versionId = profile.versionId,
                            versionType = profile.versionType,
                            modLoader = profile.modLoader,
                            modLoaderVersion = profile.modLoaderVersion,
                            ramMb = profile.gameSettings.ramMb,
                            width = profile.gameSettings.width,
                            height = profile.gameSettings.height,
                            fullscreen = profile.gameSettings.fullscreen,
                            jvmArguments = profile.jvmArguments.joinToString("\n"),
                            gameArguments = profile.gameArguments.joinToString("\n"),
                            availableVersions = _uiState.value.availableVersions,
                            isLoading = false
                        )
                    }
                } else {
                    _editState.update {
                        it.copy(error = "Profile not found", isLoading = false)
                    }
                }
            }
        }
    }

    fun updateEditState(
        name: String? = null,
        versionId: String? = null,
        modLoader: ModLoader? = null,
        modLoaderVersion: String? = null,
        ramMb: Int? = null,
        width: Int? = null,
        height: Int? = null,
        fullscreen: Boolean? = null,
        jvmArguments: String? = null,
        gameArguments: String? = null
    ) {
        _editState.update { state ->
            state.copy(
                name = name ?: state.name,
                versionId = versionId ?: state.versionId,
                modLoader = modLoader ?: state.modLoader,
                modLoaderVersion = if (modLoader != null) modLoaderVersion else state.modLoaderVersion,
                ramMb = ramMb ?: state.ramMb,
                width = width ?: state.width,
                height = height ?: state.height,
                fullscreen = fullscreen ?: state.fullscreen,
                jvmArguments = jvmArguments ?: state.jvmArguments,
                gameArguments = gameArguments ?: state.gameArguments,
                nameError = null
            )
        }
    }

    fun saveProfile(): Boolean {
        val state = _editState.value

        // Validate
        if (state.name.isBlank()) {
            _editState.update { it.copy(nameError = "Profile name is required") }
            return false
        }

        if (state.versionId.isBlank()) {
            _editState.update { it.copy(error = "Please select a version") }
            return false
        }

        viewModelScope.launch {
            _editState.update { it.copy(isSaving = true) }

            try {
                // Check if name is taken
                val existingId = state.profile?.id ?: ""
                if (profileRepository.isNameTaken(state.name, existingId)) {
                    _editState.update { 
                        it.copy(
                            nameError = "A profile with this name already exists",
                            isSaving = false
                        )
                    }
                    return@launch
                }

                val version = versionRepository.getVersionById(state.versionId)
                val versionType = version?.type ?: VersionType.RELEASE

                val profile = Profile(
                    id = state.profile?.id ?: UUID.randomUUID().toString(),
                    name = state.name.trim(),
                    versionId = state.versionId,
                    versionType = versionType,
                    modLoader = state.modLoader,
                    modLoaderVersion = state.modLoaderVersion,
                    gameSettings = GameSettings(
                        ramMb = state.ramMb,
                        width = state.width,
                        height = state.height,
                        fullscreen = state.fullscreen
                    ),
                    jvmArguments = state.jvmArguments.split("\n").filter { it.isNotBlank() },
                    gameArguments = state.gameArguments.split("\n").filter { it.isNotBlank() },
                    createdAt = state.profile?.createdAt ?: System.currentTimeMillis(),
                    lastPlayed = state.profile?.lastPlayed,
                    playTime = state.profile?.playTime ?: 0
                )

                if (state.isNew) {
                    profileRepository.createProfile(profile)
                    Timber.d("Created new profile: ${profile.name}")
                } else {
                    profileRepository.updateProfile(profile)
                    Timber.d("Updated profile: ${profile.name}")
                }

                _editState.update { it.copy(isSaving = false, error = null) }

            } catch (e: Exception) {
                Timber.e(e, "Failed to save profile")
                _editState.update { 
                    it.copy(
                        isSaving = false,
                        error = "Failed to save profile: ${e.message}"
                    )
                }
            }
        }

        return true
    }

    fun showDeleteConfirmation(profile: Profile) {
        _uiState.update { it.copy(showDeleteConfirmation = profile) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            try {
                profileRepository.deleteProfile(profile.id)
                _uiState.update { it.copy(showDeleteConfirmation = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete profile: ${e.message}") }
            }
        }
    }

    fun duplicateProfile(profile: Profile) {
        viewModelScope.launch {
            try {
                var newName = "${profile.name} (Copy)"
                var counter = 2
                while (profileRepository.isNameTaken(newName)) {
                    newName = "${profile.name} (Copy $counter)"
                    counter++
                }
                profileRepository.duplicateProfile(profile.id, newName)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to duplicate profile: ${e.message}") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
        _editState.update { it.copy(error = null) }
    }

    fun clearEditState() {
        _editState.value = ProfileEditState(availableVersions = _uiState.value.availableVersions)
    }
}
