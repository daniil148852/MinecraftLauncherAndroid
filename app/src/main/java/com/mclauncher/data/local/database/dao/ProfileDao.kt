package com.mclauncher.data.local.database.dao

import androidx.room.*
import com.mclauncher.data.local.database.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY lastPlayed DESC, createdAt DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun getProfileByIdFlow(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getProfileByName(name: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE versionId = :versionId")
    fun getProfilesByVersion(versionId: String): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE modLoader = :modLoader")
    fun getProfilesByModLoader(modLoader: String): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY lastPlayed DESC LIMIT 1")
    suspend fun getLastPlayedProfile(): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY playTime DESC LIMIT :limit")
    fun getMostPlayedProfiles(limit: Int): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("UPDATE profiles SET lastPlayed = :timestamp WHERE id = :profileId")
    suspend fun updateLastPlayed(profileId: String, timestamp: Long)

    @Query("UPDATE profiles SET playTime = playTime + :additionalTime WHERE id = :profileId")
    suspend fun addPlayTime(profileId: String, additionalTime: Long)

    @Query("UPDATE profiles SET versionId = :versionId, versionType = :versionType WHERE id = :profileId")
    suspend fun updateProfileVersion(profileId: String, versionId: String, versionType: String)

    @Query("UPDATE profiles SET modLoader = :modLoader, modLoaderVersion = :modLoaderVersion WHERE id = :profileId")
    suspend fun updateModLoader(profileId: String, modLoader: String, modLoaderVersion: String?)

    @Query("UPDATE profiles SET ramMb = :ramMb WHERE id = :profileId")
    suspend fun updateRam(profileId: String, ramMb: Int)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: String)

    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE name = :name AND id != :excludeId)")
    suspend fun isNameTaken(name: String, excludeId: String = ""): Boolean

    @Transaction
    suspend fun duplicateProfile(profileId: String, newName: String): ProfileEntity? {
        val original = getProfileById(profileId) ?: return null
        val duplicate = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName,
            createdAt = System.currentTimeMillis(),
            lastPlayed = null,
            playTime = 0
        )
        insertProfile(duplicate)
        return duplicate
    }
}
