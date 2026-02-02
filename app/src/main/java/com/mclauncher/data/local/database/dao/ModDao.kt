package com.mclauncher.data.local.database.dao

import androidx.room.*
import com.mclauncher.data.local.database.entities.ModEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModDao {

    @Query("SELECT * FROM mods ORDER BY name ASC")
    fun getAllMods(): Flow<List<ModEntity>>

    @Query("SELECT * FROM mods WHERE profileId = :profileId ORDER BY name ASC")
    fun getModsByProfile(profileId: String): Flow<List<ModEntity>>

    @Query("SELECT * FROM mods WHERE profileId = :profileId AND isEnabled = 1 ORDER BY name ASC")
    fun getEnabledModsByProfile(profileId: String): Flow<List<ModEntity>>

    @Query("SELECT * FROM mods WHERE profileId = :profileId AND isEnabled = 0 ORDER BY name ASC")
    fun getDisabledModsByProfile(profileId: String): Flow<List<ModEntity>>

    @Query("SELECT * FROM mods WHERE id = :id")
    suspend fun getModById(id: String): ModEntity?

    @Query("SELECT * FROM mods WHERE id = :id")
    fun getModByIdFlow(id: String): Flow<ModEntity?>

    @Query("SELECT * FROM mods WHERE fileName = :fileName AND profileId = :profileId LIMIT 1")
    suspend fun getModByFileName(fileName: String, profileId: String): ModEntity?

    @Query("SELECT * FROM mods WHERE modLoader = :modLoader ORDER BY name ASC")
    fun getModsByLoader(modLoader: String): Flow<List<ModEntity>>

    @Query("SELECT * FROM mods WHERE name LIKE :query OR description LIKE :query ORDER BY name ASC")
    fun searchMods(query: String): Flow<List<ModEntity>>

    @Query("SELECT * FROM mods WHERE profileId = :profileId AND (name LIKE :query OR description LIKE :query) ORDER BY name ASC")
    fun searchModsInProfile(profileId: String, query: String): Flow<List<ModEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMod(mod: ModEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMods(mods: List<ModEntity>)

    @Update
    suspend fun updateMod(mod: ModEntity)

    @Query("UPDATE mods SET isEnabled = :isEnabled WHERE id = :modId")
    suspend fun setModEnabled(modId: String, isEnabled: Boolean)

    @Query("UPDATE mods SET isEnabled = 1 WHERE profileId = :profileId")
    suspend fun enableAllMods(profileId: String)

    @Query("UPDATE mods SET isEnabled = 0 WHERE profileId = :profileId")
    suspend fun disableAllMods(profileId: String)

    @Query("UPDATE mods SET isEnabled = NOT isEnabled WHERE id = :modId")
    suspend fun toggleMod(modId: String)

    @Delete
    suspend fun deleteMod(mod: ModEntity)

    @Query("DELETE FROM mods WHERE id = :modId")
    suspend fun deleteModById(modId: String)

    @Query("DELETE FROM mods WHERE profileId = :profileId")
    suspend fun deleteModsByProfile(profileId: String)

    @Query("DELETE FROM mods")
    suspend fun deleteAllMods()

    @Query("SELECT COUNT(*) FROM mods WHERE profileId = :profileId")
    suspend fun getModCountForProfile(profileId: String): Int

    @Query("SELECT COUNT(*) FROM mods WHERE profileId = :profileId AND isEnabled = 1")
    suspend fun getEnabledModCountForProfile(profileId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM mods WHERE fileName = :fileName AND profileId = :profileId)")
    suspend fun modExists(fileName: String, profileId: String): Boolean

    @Transaction
    suspend fun copyModsToProfile(sourceProfileId: String, targetProfileId: String) {
        val mods = getModsByProfileSync(sourceProfileId)
        val copiedMods = mods.map { mod ->
            mod.copy(
                id = java.util.UUID.randomUUID().toString(),
                profileId = targetProfileId,
                addedAt = System.currentTimeMillis()
            )
        }
        insertMods(copiedMods)
    }

    @Query("SELECT * FROM mods WHERE profileId = :profileId")
    suspend fun getModsByProfileSync(profileId: String): List<ModEntity>
}
