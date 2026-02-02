package com.mclauncher.data.repository

import com.mclauncher.data.local.database.dao.ProfileDao
import com.mclauncher.data.local.database.entities.ProfileEntity
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.domain.models.GameSettings
import com.mclauncher.domain.models.ModLoader
import com.mclauncher.domain.models.Profile
import com.mclauncher.domain.models.VersionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val preferencesManager: PreferencesManager
) {

    fun getAllProfiles(): Flow<List<Profile>> {
        return profileDao.getAllProfiles().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getProfileById(id: String): Profile? {
        return profileDao.getProfileById(id)?.toDomainModel()
    }

    fun getProfileByIdFlow(id: String): Flow<Profile?> {
        return profileDao.getProfileByIdFlow(id).map { it?.toDomainModel() }
    }

    suspend fun getProfileByName(name: String): Profile? {
        return profileDao.getProfileByName(name)?.toDomainModel()
    }

    fun getProfilesByVersion(versionId: String): Flow<List<Profile>> {
        return profileDao.getProfilesByVersion(versionId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getProfilesByModLoader(modLoader: ModLoader): Flow<List<Profile>> {
        return profileDao.getProfilesByModLoader(modLoader.name.lowercase()).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getLastPlayedProfile(): Profile? {
        return profileDao.getLastPlayedProfile()?.toDomainModel()
    }

    fun getMostPlayedProfiles(limit: Int = 5): Flow<List<Profile>> {
        return profileDao.getMostPlayedProfiles(limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun createProfile(profile: Profile): String {
        val entity = profile.toEntity()
        profileDao.insertProfile(entity)
        Timber.d("Created profile: ${profile.name} (${profile.id})")
        return profile.id
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.updateProfile(profile.toEntity())
        Timber.d("Updated profile: ${profile.name}")
    }

    suspend fun updateLastPlayed(profileId: String) {
        val timestamp = System.currentTimeMillis()
        profileDao.updateLastPlayed(profileId, timestamp)
        preferencesManager.updatePreference(PreferencesManager.LAST_PLAYED_VERSION, profileId)
    }

    suspend fun addPlayTime(profileId: String, additionalTimeMs: Long) {
        profileDao.addPlayTime(profileId, additionalTimeMs)
    }

    suspend fun updateProfileVersion(profileId: String, versionId: String, versionType: VersionType) {
        profileDao.updateProfileVersion(profileId, versionId, versionType.name.lowercase())
    }

    suspend fun updateModLoader(profileId: String, modLoader: ModLoader, modLoaderVersion: String?) {
        profileDao.updateModLoader(profileId, modLoader.name.lowercase(), modLoaderVersion)
    }

    suspend fun updateRam(profileId: String, ramMb: Int) {
        profileDao.updateRam(profileId, ramMb)
    }

    suspend fun deleteProfile(profileId: String) {
        profileDao.deleteProfileById(profileId)
        Timber.d("Deleted profile: $profileId")
    }

    suspend fun duplicateProfile(profileId: String, newName: String): Profile? {
        return profileDao.duplicateProfile(profileId, newName)?.toDomainModel()
    }

    suspend fun isNameTaken(name: String, excludeId: String = ""): Boolean {
        return profileDao.isNameTaken(name, excludeId)
    }

    suspend fun getProfileCount(): Int {
        return profileDao.getProfileCount()
    }

    suspend fun setSelectedProfile(profileId: String) {
        preferencesManager.setSelectedProfile(profileId)
    }

    private fun ProfileEntity.toDomainModel(): Profile {
        return Profile(
            id = id,
            name = name,
            versionId = versionId,
            versionType = VersionType.fromString(versionType),
            modLoader = ModLoader.fromString(modLoader),
            modLoaderVersion = modLoaderVersion,
            gameDirectory = gameDirectory?.let { File(it) },
            javaPath = javaPath,
            jvmArguments = jvmArguments?.split("|||")?.filter { it.isNotBlank() } ?: emptyList(),
            gameArguments = gameArguments?.split("|||")?.filter { it.isNotBlank() } ?: emptyList(),
            gameSettings = GameSettings(
                ramMb = ramMb,
                width = width,
                height = height,
                fullscreen = fullscreen
            ),
            iconPath = iconPath,
            createdAt = createdAt,
            lastPlayed = lastPlayed,
            playTime = playTime
        )
    }

    private fun Profile.toEntity(): ProfileEntity {
        return ProfileEntity(
            id = id,
            name = name,
            versionId = versionId,
            versionType = versionType.name.lowercase(),
            modLoader = modLoader.name.lowercase(),
            modLoaderVersion = modLoaderVersion,
            gameDirectory = gameDirectory?.absolutePath,
            javaPath = javaPath,
            jvmArguments = jvmArguments.joinToString("|||"),
            gameArguments = gameArguments.joinToString("|||"),
            ramMb = gameSettings.ramMb,
            width = gameSettings.width,
            height = gameSettings.height,
            fullscreen = gameSettings.fullscreen,
            iconPath = iconPath,
            createdAt = createdAt,
            lastPlayed = lastPlayed,
            playTime = playTime
        )
    }
}
