package com.mclauncher.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.core.modloader.ModManager
import com.mclauncher.data.repository.ModRepository
import com.mclauncher.domain.models.Mod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ModDetailUiState(
    val mod: Mod? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ModDetailViewModel @Inject constructor(
    private val modRepository: ModRepository,
    private val modManager: ModManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModDetailUiState())
    val uiState: StateFlow<ModDetailUiState> = _uiState.asStateFlow()

    private var currentModId: String? = null

    fun loadMod(modId: String) {
        currentModId = modId
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val mod = modRepository.getModById(modId)
                _uiState.update {
                    it.copy(
                        mod = mod,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load mod")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load mod: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleMod() {
        val mod = _uiState.value.mod ?: return

        viewModelScope.launch {
            try {
                modManager.toggleMod(mod)
                // Reload mod
                loadMod(mod.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to toggle mod: ${e.message}") }
            }
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteMod() {
        val mod = _uiState.value.mod ?: return

        viewModelScope.launch {
            try {
                modManager.deleteMod(mod)
                _uiState.update {
                    it.copy(
                        showDeleteDialog = false,
                        deleted = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showDeleteDialog = false,
                        error = "Failed to delete mod: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
