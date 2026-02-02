package com.mclauncher.data.repository

import com.mclauncher.data.local.database.dao.AccountDao
import com.mclauncher.data.local.database.entities.AccountEntity
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.domain.models.Account
import com.mclauncher.domain.models.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val preferencesManager: PreferencesManager
) {

    fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getOfflineAccounts(): Flow<List<Account>> {
        return accountDao.getOfflineAccounts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getMicrosoftAccounts(): Flow<List<Account>> {
        return accountDao.getMicrosoftAccounts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getActiveAccount(): Account? {
        return accountDao.getActiveAccount()?.toDomainModel()
    }

    fun getActiveAccountFlow(): Flow<Account?> {
        return accountDao.getActiveAccountFlow().map { it?.toDomainModel() }
    }

    suspend fun getAccountById(id: String): Account? {
        return accountDao.getAccountById(id)?.toDomainModel()
    }

    fun getAccountByIdFlow(id: String): Flow<Account?> {
        return accountDao.getAccountByIdFlow(id).map { it?.toDomainModel() }
    }

    suspend fun getAccountByUsername(username: String, type: AccountType): Account? {
        return accountDao.getAccountByUsername(username, type.name.lowercase())?.toDomainModel()
    }

    suspend fun createOfflineAccount(username: String): Result<Account> {
        return try {
            // Validate username
            if (username.isBlank()) {
                return Result.failure(Exception("Username cannot be empty"))
            }
            if (username.length < 3 || username.length > 16) {
                return Result.failure(Exception("Username must be 3-16 characters"))
            }
            if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                return Result.failure(Exception("Username can only contain letters, numbers, and underscores"))
            }

            // Check if username already exists
            if (accountDao.usernameExists(username, "offline")) {
                return Result.failure(Exception("An offline account with this username already exists"))
            }

            // Generate offline UUID (consistent for same username)
            val offlineUuid = generateOfflineUuid(username)

            val account = Account(
                id = UUID.randomUUID().toString(),
                username = username,
                uuid = offlineUuid,
                accountType = AccountType.OFFLINE,
                isActive = false,
                createdAt = System.currentTimeMillis()
            )

            accountDao.insertAccount(account.toEntity())
            Timber.d("Created offline account: $username ($offlineUuid)")

            Result.success(account)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create offline account")
            Result.failure(e)
        }
    }

    suspend fun setActiveAccount(accountId: String) {
        accountDao.setActiveAccount(accountId)
        preferencesManager.setActiveAccount(accountId)
        Timber.d("Set active account: $accountId")
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account.toEntity())
    }

    suspend fun updateTokens(
        accountId: String,
        accessToken: String?,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        accountDao.updateTokens(accountId, accessToken, refreshToken, expiresAt)
    }

    suspend fun updateSkin(accountId: String, skinUrl: String?, capeUrl: String?) {
        accountDao.updateSkin(accountId, skinUrl, capeUrl)
    }

    suspend fun updateUsername(accountId: String, newUsername: String) {
        accountDao.updateUsername(accountId, newUsername)
    }

    suspend fun deleteAccount(accountId: String) {
        val account = accountDao.getAccountById(accountId)
        if (account?.isActive == true) {
            // If deleting active account, clear the active account preference
            preferencesManager.setActiveAccount(null)
        }
        accountDao.deleteAccountById(accountId)
        Timber.d("Deleted account: $accountId")
    }

    suspend fun deleteAllOfflineAccounts() {
        accountDao.deleteAllOfflineAccounts()
    }

    suspend fun getAccountCount(): Int {
        return accountDao.getAccountCount()
    }

    suspend fun usernameExists(username: String, type: AccountType): Boolean {
        return accountDao.usernameExists(username, type.name.lowercase())
    }

    suspend fun hasExpiredTokens(): Boolean {
        return accountDao.hasExpiredTokens()
    }

    suspend fun getAccountsWithExpiredTokens(): List<Account> {
        return accountDao.getAccountsWithExpiredTokens().map { it.toDomainModel() }
    }

    /**
     * Generates a consistent offline UUID for a username.
     * This matches how Minecraft generates offline UUIDs.
     */
    private fun generateOfflineUuid(username: String): String {
        val data = "OfflinePlayer:$username".toByteArray(Charsets.UTF_8)
        val md = java.security.MessageDigest.getInstance("MD5")
        val hash = md.digest(data)
        
        // Set version to 3 (name-based)
        hash[6] = (hash[6].toInt() and 0x0f or 0x30).toByte()
        // Set variant to IETF
        hash[8] = (hash[8].toInt() and 0x3f or 0x80).toByte()
        
        val sb = StringBuilder()
        for (i in hash.indices) {
            sb.append(String.format("%02x", hash[i]))
            if (i == 3 || i == 5 || i == 7 || i == 9) {
                sb.append("-")
            }
        }
        return sb.toString()
    }

    private fun AccountEntity.toDomainModel(): Account {
        return Account(
            id = id,
            username = username,
            uuid = uuid,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accountType = AccountType.valueOf(accountType.uppercase()),
            skinUrl = skinUrl,
            capeUrl = capeUrl,
            isActive = isActive,
            createdAt = createdAt,
            lastUsed = lastUsed,
            expiresAt = expiresAt
        )
    }

    private fun Account.toEntity(): AccountEntity {
        return AccountEntity(
            id = id,
            username = username,
            uuid = uuid,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accountType = accountType.name.lowercase(),
            skinUrl = skinUrl,
            capeUrl = capeUrl,
            isActive = isActive,
            createdAt = createdAt,
            lastUsed = lastUsed,
            expiresAt = expiresAt
        )
    }
}
