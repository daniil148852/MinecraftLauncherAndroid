package com.mclauncher.data.local.database.dao

import androidx.room.*
import com.mclauncher.data.local.database.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY lastUsed DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE accountType = :type ORDER BY lastUsed DESC")
    fun getAccountsByType(type: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE accountType = 'offline' ORDER BY lastUsed DESC")
    fun getOfflineAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE accountType = 'microsoft' ORDER BY lastUsed DESC")
    fun getMicrosoftAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccount(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccountFlow(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getAccountByIdFlow(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE username = :username AND accountType = :type LIMIT 1")
    suspend fun getAccountByUsername(username: String, type: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE uuid = :uuid LIMIT 1")
    suspend fun getAccountByUuid(uuid: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun deactivateAllAccounts()

    @Query("UPDATE accounts SET isActive = 1 WHERE id = :accountId")
    suspend fun activateAccount(accountId: String)

    @Transaction
    suspend fun setActiveAccount(accountId: String) {
        deactivateAllAccounts()
        activateAccount(accountId)
        updateLastUsed(accountId, System.currentTimeMillis())
    }

    @Query("UPDATE accounts SET lastUsed = :timestamp WHERE id = :accountId")
    suspend fun updateLastUsed(accountId: String, timestamp: Long)

    @Query("UPDATE accounts SET accessToken = :accessToken, refreshToken = :refreshToken, expiresAt = :expiresAt WHERE id = :accountId")
    suspend fun updateTokens(accountId: String, accessToken: String?, refreshToken: String?, expiresAt: Long?)

    @Query("UPDATE accounts SET skinUrl = :skinUrl, capeUrl = :capeUrl WHERE id = :accountId")
    suspend fun updateSkin(accountId: String, skinUrl: String?, capeUrl: String?)

    @Query("UPDATE accounts SET username = :username WHERE id = :accountId")
    suspend fun updateUsername(accountId: String, username: String)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: String)

    @Query("DELETE FROM accounts WHERE accountType = 'offline'")
    suspend fun deleteAllOfflineAccounts()

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE accountType = :type")
    suspend fun getAccountCountByType(type: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE username = :username AND accountType = :type)")
    suspend fun usernameExists(username: String, type: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE expiresAt IS NOT NULL AND expiresAt < :currentTime)")
    suspend fun hasExpiredTokens(currentTime: Long = System.currentTimeMillis()): Boolean

    @Query("SELECT * FROM accounts WHERE expiresAt IS NOT NULL AND expiresAt < :currentTime")
    suspend fun getAccountsWithExpiredTokens(currentTime: Long = System.currentTimeMillis()): List<AccountEntity>
}
