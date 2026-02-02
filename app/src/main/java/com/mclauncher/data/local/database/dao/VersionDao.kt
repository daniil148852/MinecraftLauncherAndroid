package com.mclauncher.data.local.database.dao

import androidx.room.*
import com.mclauncher.data.local.database.entities.VersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VersionDao {

    @Query("SELECT * FROM versions ORDER BY releaseTime DESC")
    fun getAllVersions(): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versions WHERE type = :type ORDER BY releaseTime DESC")
    fun getVersionsByType(type: String): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versions WHERE type IN (:types) ORDER BY releaseTime DESC")
    fun getVersionsByTypes(types: List<String>): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versions WHERE isInstalled = 1 ORDER BY releaseTime DESC")
    fun getInstalledVersions(): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versions WHERE id = :id")
    suspend fun getVersionById(id: String): VersionEntity?

    @Query("SELECT * FROM versions WHERE id = :id")
    fun getVersionByIdFlow(id: String): Flow<VersionEntity?>

    @Query("SELECT * FROM versions WHERE type = 'release' ORDER BY releaseTime DESC LIMIT 1")
    suspend fun getLatestRelease(): VersionEntity?

    @Query("SELECT * FROM versions WHERE type = 'snapshot' ORDER BY releaseTime DESC LIMIT 1")
    suspend fun getLatestSnapshot(): VersionEntity?

    @Query("SELECT * FROM versions WHERE id LIKE :query OR type LIKE :query ORDER BY releaseTime DESC")
    fun searchVersions(query: String): Flow<List<VersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: VersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersions(versions: List<VersionEntity>)

    @Update
    suspend fun updateVersion(version: VersionEntity)

    @Query("UPDATE versions SET isInstalled = :isInstalled, installPath = :installPath WHERE id = :versionId")
    suspend fun updateInstallStatus(versionId: String, isInstalled: Boolean, installPath: String?)

    @Query("UPDATE versions SET mainClass = :mainClass, assetsId = :assetsId, javaVersion = :javaVersion WHERE id = :versionId")
    suspend fun updateVersionDetails(versionId: String, mainClass: String?, assetsId: String?, javaVersion: Int?)

    @Query("UPDATE versions SET downloadedSize = :downloadedSize, totalSize = :totalSize WHERE id = :versionId")
    suspend fun updateDownloadProgress(versionId: String, downloadedSize: Long, totalSize: Long)

    @Delete
    suspend fun deleteVersion(version: VersionEntity)

    @Query("DELETE FROM versions WHERE id = :versionId")
    suspend fun deleteVersionById(versionId: String)

    @Query("DELETE FROM versions WHERE isInstalled = 0")
    suspend fun deleteUninstalledVersions()

    @Query("DELETE FROM versions")
    suspend fun deleteAllVersions()

    @Query("SELECT COUNT(*) FROM versions")
    suspend fun getVersionCount(): Int

    @Query("SELECT COUNT(*) FROM versions WHERE isInstalled = 1")
    suspend fun getInstalledVersionCount(): Int

    @Query("SELECT COUNT(*) FROM versions WHERE type = :type")
    suspend fun getVersionCountByType(type: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM versions WHERE id = :versionId AND isInstalled = 1)")
    suspend fun isVersionInstalled(versionId: String): Boolean

    @Query("""
        SELECT * FROM versions 
        WHERE (:includeReleases = 1 AND type = 'release')
           OR (:includeSnapshots = 1 AND type = 'snapshot')
           OR (:includeOldBeta = 1 AND type = 'old_beta')
           OR (:includeOldAlpha = 1 AND type = 'old_alpha')
        ORDER BY releaseTime DESC
    """)
    fun getFilteredVersions(
        includeReleases: Boolean = true,
        includeSnapshots: Boolean = false,
        includeOldBeta: Boolean = false,
        includeOldAlpha: Boolean = false
    ): Flow<List<VersionEntity>>
}
