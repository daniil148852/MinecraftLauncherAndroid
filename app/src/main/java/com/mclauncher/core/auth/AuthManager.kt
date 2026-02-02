package com.mclauncher.core.auth

import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.repository.AccountRepository
import com.mclauncher.domain.models.Account
import com.mclauncher.domain.models.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val offlineAuthProvider: OfflineAuthProvider,
    private val microsoftAuthProvider: MicrosoftAuthProvider,
    private val preferencesManager: PreferencesManager
) {
    fun getAllAccounts(): Flow<List<Account>> = accountRepository.getAllAccounts()

    fun getActiveAccountFlow(): Flow<Account?> = accountRepository.getActiveAccountFlow()

    suspend fun getActiveAccount(): Account? = accountRepository.getActiveAccount()

    suspend fun createOfflineAccount(username: String): Result<Account> {
        return try {
            // Validate username
            val validationResult = offlineAuthProvider.validateUsername(username)
            if (validationResult.isFailure) {
                return Result.failure(validationResult.exceptionOrNull()!!)
            }

            // Create account
            val result = accountRepository.createOfflineAccount(username)
            if (result.isSuccess) {
                val account = result.getOrThrow()
                
                // If this is the first account, set it as active
                val accountCount = accountRepository.getAccountCount()
                if (accountCount == 1) {
                    accountRepository.setActiveAccount(account.id)
                }
                
                Timber.d("Created offline account: ${account.username}")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to create offline account")
            Result.failure(e)
        }
    }

    suspend fun loginWithMicrosoft(authCode: String): Result<Account> {
        return try {
            val result = microsoftAuthProvider.authenticate(authCode)
            if (result.isSuccess) {
                val authResult = result.getOrThrow()
                
                // Check if account already exists
                val existingAccount = accountRepository.getAccountByUsername(
                    authResult.username,
                    AccountType.MICROSOFT
                )

                val account = if (existingAccount != null) {
                    // Update existing account
                    val updated = existingAccount.copy(
                        accessToken = authResult.accessToken,
                        refreshToken = authResult.refreshToken,
                        expiresAt = authResult.expiresAt,
                        skinUrl = authResult.skinUrl,
                        capeUrl = authResult.capeUrl,
                        lastUsed = System.currentTimeMillis()
                    )
                    accountRepository.updateAccount(updated)
                    updated
                } else {
                    // Create new account
                    val newAccount = Account(
                        username = authResult.username,
                        uuid = authResult.uuid,
                        accessToken = authResult.accessToken,
                        refreshToken = authResult.refreshToken,
                        accountType = AccountType.MICROSOFT,
                        skinUrl = authResult.skinUrl,
                        capeUrl = authResult.capeUrl,
                        expiresAt = authResult.expiresAt
                    )
                    accountRepository.createOfflineAccount(authResult.username) // This will be overridden
                    newAccount
                }

                // Set as active if first account
                val accountCount = accountRepository.getAccountCount()
                if (accountCount == 1) {
                    accountRepository.setActiveAccount(account.id)
                }

                Timber.d("Logged in with Microsoft: ${account.username}")
                Result.success(account)
            } else {
                result.map { throw Exception("Unexpected null result") }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to login with Microsoft")
            Result.failure(e)
        }
    }

    suspend fun refreshToken(accountId: String): Result<Account> {
        return try {
            val account = accountRepository.getAccountById(accountId)
                ?: return Result.failure(Exception("Account not found"))

            if (account.accountType != AccountType.MICROSOFT) {
                return Result.failure(Exception("Cannot refresh token for offline account"))
            }

            val refreshToken = account.refreshToken
                ?: return Result.failure(Exception("No refresh token available"))

            val result = microsoftAuthProvider.refreshToken(refreshToken)
            if (result.isSuccess) {
                val authResult = result.getOrThrow()
                accountRepository.updateTokens(
                    accountId,
                    authResult.accessToken,
                    authResult.refreshToken,
                    authResult.expiresAt
                )

                val updatedAccount = account.copy(
                    accessToken = authResult.accessToken,
                    refreshToken = authResult.refreshToken,
                    expiresAt = authResult.expiresAt
                )
                Result.success(updatedAccount)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh token")
            Result.failure(e)
        }
    }

    suspend fun setActiveAccount(accountId: String) {
        accountRepository.setActiveAccount(accountId)
        Timber.d("Set active account: $accountId")
    }

    suspend fun deleteAccount(accountId: String) {
        accountRepository.deleteAccount(accountId)
        Timber.d("Deleted account: $accountId")
    }

    suspend fun updateUsername(accountId: String, newUsername: String): Result<Unit> {
        return try {
            val account = accountRepository.getAccountById(accountId)
                ?: return Result.failure(Exception("Account not found"))

            if (account.accountType != AccountType.OFFLINE) {
                return Result.failure(Exception("Can only change username for offline accounts"))
            }

            val validationResult = offlineAuthProvider.validateUsername(newUsername)
            if (validationResult.isFailure) {
                return Result.failure(validationResult.exceptionOrNull()!!)
            }

            if (accountRepository.usernameExists(newUsername, AccountType.OFFLINE)) {
                return Result.failure(Exception("Username already taken"))
            }

            accountRepository.updateUsername(accountId, newUsername)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update username")
            Result.failure(e)
        }
    }

    suspend fun validateActiveAccount(): Result<Account> {
        val account = getActiveAccount()
            ?: return Result.failure(Exception("No active account"))

        return when (account.accountType) {
            AccountType.OFFLINE -> Result.success(account)
            AccountType.MICROSOFT -> {
                if (account.isExpired) {
                    refreshToken(account.id)
                } else {
                    Result.success(account)
                }
            }
        }
    }

    suspend fun getAuthenticatedAccount(): Account? {
        val result = validateActiveAccount()
        return result.getOrNull()
    }

    fun getMicrosoftLoginUrl(): String {
        return microsoftAuthProvider.getLoginUrl()
    }
}
