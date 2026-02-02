package com.mclauncher.data.repository

import com.mclauncher.data.local.database.dao.VersionDao
import com.mclauncher.data.local.database.entities.VersionEntity
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.remote.api.MojangApi
import com.mclauncher.data.remote.models.VersionDetails
import com.mclauncher.data.remote.models.VersionManifest
import com.mclauncher.domain.models.GameVersion
import com.mclauncher.domain.models.VersionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionRepository @Inject constructor(
    private val mojangApi: MojangApi,
    private val versionDao: VersionDao,
    private val preferencesManager: PreferencesManager
) {
    
    suspend fun fetchVersionManifest(): Result<VersionManifest> = withContext(Dispatchers.IO) {
        try {
            val response = mojangApi.getVersionManifest()
            if (response.isSuccessful && response.body() != null) {
                val manifest = response.body()!!
                
                // Cache versions to database
                val entities = manifest.versions.map { versionInfo ->
                    val existing = versionDao.getVersionById(versionInfo.id)
                    VersionEntity(
                        id = versionInfo.id,
                        type = versionInfo.type,
                        url = versionInfo.url,
                        sha1 = versionInfo.sha1,
                        releaseTime = versionInfo.releaseTime,
                        isInstalled = existing?.isInstalled ?: false,
                        installPath = existing?.installPath,
                        mainClass = existing?.mainClass,
                        assetsId = existing?.assetsId,
                        javaVersion = existing?.javaVersion,
                        totalSize = existing?.totalSize,
                        downloadedSize = existing?.downloadedSize
                    )
                }
                versionDao.insertVersions(entities)
                
                Timber.d("Fetched ${manifest.versions.size} versions from Mojang API")
                Result.success(manifest)
            } else {
                Result.failure(Exception("Failed to fetch version manifest: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching version manifest")
            Result.failure(e)
        }
    }

    suspend fun fetchVersionDetails(versionId: String): Result<VersionDetails> = withContext(Dispatchers.IO) {
        try {
            val version = versionDao.getVersionById(versionId)
                ?: return@withContext Result.failure(Exception("Version not found: $versionId"))

            val response = mojangApi.getVersionDetails(version.url)
            if (response.isSuccessful && response.body() != null) {
                val details = response.body()!!
                
                // Update version in database with details
                versionDao.updateVersionDetails(
                    versionId = versionId,
                    mainClass = details.mainClass,
                    assetsId = details.assetIndex.id,
                    javaVersion = details.javaVersion?.majorVersion
                )
                
                Result.success(details)
            } else {
                Result.failure(Exception("Failed to fetch version details: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching version details for $versionId")
            Result.failure(e)
        }
    }

    fun getAllVersions(): Flow<List<GameVersion>> {
        return versionDao.getAllVersions().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getFilteredVersions(): Flow<List<GameVersion>> {
        return preferencesManager.preferences.map { prefs ->
            versionDao.getFilteredVersions(
                includeReleases = prefs.showReleases,
                includeSnapshots = prefs.showSnapshots,
                includeOldBeta = prefs.showOldBeta,
                includeOldAlpha = prefs.showOldAlpha
            ).first()
        }.map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getVersionsByType(type: VersionType): Flow<List<GameVersion>> {
        return versionDao.getVersionsByType(type.name.lowercase()).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getInstalledVersions(): Flow<List<GameVersion>> {
        return versionDao.getInstalledVersions().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getVersionById(id: String): GameVersion? {
        return versionDao.getVersionById(id)?.toDomainModel()
    }

    fun getVersionByIdFlow(id: String): Flow<GameVersion?> {
        return versionDao.getVersionByIdFlow(id).map { it?.toDomainModel() }
    }

    suspend fun getLatestRelease(): GameVersion? {
        return versionDao.getLatestRelease()?.toDomainModel()
    }

    suspend fun getLatestSnapshot(): GameVersion? {
        return versionDao.getLatestSnapshot()?.toDomainModel()
    }

    fun searchVersions(query: String): Flow<List<GameVersion>> {
        return versionDao.searchVersions("%$query%").map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun markVersionInstalled(versionId: String, installPath: String) {
        versionDao.updateInstallStatus(versionId, true, installPath)
    }

    suspend fun markVersionUninstalled(versionId: String) {
        versionDao.updateInstallStatus(versionId, false, null)
    }

    suspend fun updateDownloadProgress(versionId: String, downloaded: Long, total: Long) {
        versionDao.updateDownloadProgress(versionId, downloaded, total)
    }

    suspend fun isVersionInstalled(versionId: String): Boolean {
        return versionDao.isVersionInstalled(versionId)
    }

    suspend fun deleteVersion(versionId: String) {
        versionDao.deleteVersionById(versionId)
    }

    private fun VersionEntity.toDomainModel(): GameVersion {
        return GameVersion(
            id = id,
            type = VersionType.fromString(type),
            url = url,
            sha1 = sha1,
            releaseTime = releaseTime,
            isInstalled = isInstalled,
            installPath = installPath,
            mainClass = mainClass,
            assetsId = assetsId,
            javaVersion = javaVersion,
            totalSize = totalSize,
            downloadedSize = downloadedSize
        )
    }
}
