package com.mclauncher.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.core.modloader.ModManager
import com.mclauncher.data.repository.ModRepository
import com.mclauncher.data.repository.ProfileRepository
import com.mclauncher.domain.models.Mod
import com.mclauncher.domain.models.ModLoader
import com.mclauncher.domain.models.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ModsUiState(
    val mods: List<Mod> = emptyList(),
    val filteredMods: List<Mod> = emptyList(),
    val selectedProfile: Profile? = null,
    val availableProfiles: List<Profile> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val showDeleteConfirmation: Mod? = null,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ModsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modRepository: ModRepository,
    private val profileRepository: ProfileRepository,
    private val modManager: ModManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModsUiState())
    val uiState: StateFlow<ModsUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            profileRepository.getAllProfiles().collect { profiles ->
                _uiState.update { state ->
                    // Filter to only profiles with mod loaders
                    val moddableProfiles = profiles.filter { it.modLoader != ModLoader.NONE }
                    
                    // Auto-select first profile if none selected
                    val selectedProfile = state.selectedProfile 
                        ?: moddableProfiles.firstOrNull()
                    
                    state.copy(
                        availableProfiles = profiles,
                        selectedProfile = selectedProfile,
                        isLoading = false
                    )
                }

                // Load mods for selected profile
                _uiState.value.selectedProfile?.let { profile ->
                    loadModsForProfile(profile.id)
                }
            }
        }
    }

    private fun loadModsForProfile(profileId: String) {
        viewModelScope.launch {
            modRepository.getModsByProfile(profileId).collect { mods ->
                _uiState.update { state ->
                    state.copy(
                        mods = mods,
                        filteredMods = filterMods(mods, state.searchQuery)
                    )
                }
            }
        }
    }

    fun selectProfile(profile: Profile) {
        _uiState.update { it.copy(selectedProfile = profile) }
        loadModsForProfile(profile.id)
    }

    fun search(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredMods = filterMods(state.mods, query)
            )
        }
    }

    private fun filterMods(mods: List<Mod>, query: String): List<Mod> {
        if (query.isBlank()) return mods
        return mods.filter { mod ->
            mod.name.contains(query, ignoreCase = true) ||
            mod.description?.contains(query, ignoreCase = true) == true
        }
    }

    fun importMods(uris: List<Uri>) {
        val profile = _uiState.value.selectedProfile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }

            var successCount = 0
            var failCount = 0

            uris.forEach { uri ->
                val result = modManager.importMod(uri, profile)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                    Timber.e(result.exceptionOrNull(), "Failed to import mod")
                }
            }

            _uiState.update {
                it.copy(
                    isImporting = false,
                    successMessage = when {
                        failCount == 0 -> "Imported $successCount mod(s) successfully"
                        successCount == 0 -> "Failed to import mods"
                        else -> "Imported $successCount mod(s), $failCount failed"
                    }
                )
            }
        }
    }

    fun toggleMod(mod: Mod) {
        viewModelScope.launch {
            try {
                modManager.toggleMod(mod)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to toggle mod: ${e.message}") }
            }
        }
    }

    fun showDeleteConfirmation(mod: Mod) {
        _uiState.update { it.copy(showDeleteConfirmation = mod) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }

    fun deleteMod(mod: Mod) {
        viewModelScope.launch {
            try {
                modManager.deleteMod(mod)
                _uiState.update {
                    it.copy(
                        showDeleteConfirmation = null,
                        successMessage = "Deleted ${mod.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showDeleteConfirmation = null,
                        error = "Failed to delete mod: ${e.message}"
                    )
                }
            }
        }
    }

    fun enableAllMods() {
        val profile = _uiState.value.selectedProfile ?: return
        viewModelScope.launch {
            modRepository.enableAllMods(profile.id)
        }
    }

    fun disableAllMods() {
        val profile = _uiState.value.selectedProfile ?: return
        viewModelScope.launch {
            modRepository.disableAllMods(profile.id)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
