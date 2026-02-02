package com.mclauncher.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.core.auth.AuthManager
import com.mclauncher.domain.models.Account
import com.mclauncher.domain.models.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AccountUiState(
    val accounts: List<Account> = emptyList(),
    val activeAccount: Account? = null,
    val isLoading: Boolean = true,
    val showAddOfflineDialog: Boolean = false,
    val showEditUsernameDialog: Account? = null,
    val showDeleteConfirmation: Account? = null,
    val offlineUsername: String = "",
    val offlineUsernameError: String? = null,
    val editUsername: String = "",
    val editUsernameError: String? = null,
    val microsoftLoginUrl: String? = null,
    val isAddingAccount: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            // Observe all accounts
            launch {
                authManager.getAllAccounts().collect { accounts ->
                    _uiState.update { 
                        it.copy(
                            accounts = accounts,
                            isLoading = false
                        )
                    }
                }
            }

            // Observe active account
            launch {
                authManager.getActiveAccountFlow().collect { account ->
                    _uiState.update { it.copy(activeAccount = account) }
                }
            }
        }
    }

    fun showAddOfflineDialog() {
        _uiState.update { 
            it.copy(
                showAddOfflineDialog = true,
                offlineUsername = "",
                offlineUsernameError = null
            )
        }
    }

    fun hideAddOfflineDialog() {
        _uiState.update { 
            it.copy(
                showAddOfflineDialog = false,
                offlineUsername = "",
                offlineUsernameError = null
            )
        }
    }

    fun updateOfflineUsername(username: String) {
        _uiState.update { 
            it.copy(
                offlineUsername = username,
                offlineUsernameError = null
            )
        }
    }

    fun createOfflineAccount() {
        val username = _uiState.value.offlineUsername.trim()
        
        if (username.isBlank()) {
            _uiState.update { it.copy(offlineUsernameError = "Username cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingAccount = true) }

            val result = authManager.createOfflineAccount(username)
            
            result.fold(
                onSuccess = { account ->
                    _uiState.update { 
                        it.copy(
                            showAddOfflineDialog = false,
                            isAddingAccount = false,
                            offlineUsername = "",
                            successMessage = "Account '${account.username}' created successfully"
                        )
                    }
                    
                    // Set as active if it's the only account
                    if (_uiState.value.accounts.size == 1) {
                        authManager.setActiveAccount(account.id)
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            offlineUsernameError = error.message,
                            isAddingAccount = false
                        )
                    }
                }
            )
        }
    }

    fun startMicrosoftLogin() {
        val loginUrl = authManager.getMicrosoftLoginUrl()
        _uiState.update { it.copy(microsoftLoginUrl = loginUrl) }
    }

    fun completeMicrosoftLogin(authCode: String) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isAddingAccount = true,
                    microsoftLoginUrl = null
                )
            }

            val result = authManager.loginWithMicrosoft(authCode)
            
            result.fold(
                onSuccess = { account ->
                    _uiState.update { 
                        it.copy(
                            isAddingAccount = false,
                            successMessage = "Logged in as ${account.username}"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isAddingAccount = false,
                            error = "Microsoft login failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun cancelMicrosoftLogin() {
        _uiState.update { it.copy(microsoftLoginUrl = null) }
    }

    fun setActiveAccount(account: Account) {
        viewModelScope.launch {
            authManager.setActiveAccount(account.id)
        }
    }

    fun showEditUsernameDialog(account: Account) {
        if (account.accountType != AccountType.OFFLINE) {
            _uiState.update { 
                it.copy(error = "Cannot change username for Microsoft accounts")
            }
            return
        }
        
        _uiState.update { 
            it.copy(
                showEditUsernameDialog = account,
                editUsername = account.username,
                editUsernameError = null
            )
        }
    }

    fun hideEditUsernameDialog() {
        _uiState.update { 
            it.copy(
                showEditUsernameDialog = null,
                editUsername = "",
                editUsernameError = null
            )
        }
    }

    fun updateEditUsername(username: String) {
        _uiState.update { 
            it.copy(
                editUsername = username,
                editUsernameError = null
            )
        }
    }

    fun saveUsername() {
        val account = _uiState.value.showEditUsernameDialog ?: return
        val newUsername = _uiState.value.editUsername.trim()

        if (newUsername.isBlank()) {
            _uiState.update { it.copy(editUsernameError = "Username cannot be empty") }
            return
        }

        if (newUsername == account.username) {
            hideEditUsernameDialog()
            return
        }

        viewModelScope.launch {
            val result = authManager.updateUsername(account.id, newUsername)
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            showEditUsernameDialog = null,
                            editUsername = "",
                            successMessage = "Username updated to '$newUsername'"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(editUsernameError = error.message)
                    }
                }
            )
        }
    }

    fun showDeleteConfirmation(account: Account) {
        _uiState.update { it.copy(showDeleteConfirmation = account) }
    }

    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }

    fun deleteAccount() {
        val account = _uiState.value.showDeleteConfirmation ?: return

        viewModelScope.launch {
            try {
                authManager.deleteAccount(account.id)
                _uiState.update { 
                    it.copy(
                        showDeleteConfirmation = null,
                        successMessage = "Account '${account.username}' deleted"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        showDeleteConfirmation = null,
                        error = "Failed to delete account: ${e.message}"
                    )
                }
            }
        }
    }

    fun refreshToken(account: Account) {
        if (account.accountType != AccountType.MICROSOFT) return

        viewModelScope.launch {
            val result = authManager.refreshToken(account.id)
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(successMessage = "Token refreshed successfully")
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(error = "Failed to refresh token: ${error.message}")
                    }
                }
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
