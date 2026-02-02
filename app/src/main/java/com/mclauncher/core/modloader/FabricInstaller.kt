package com.mclauncher.core.modloader

import android.content.Context
import com.mclauncher.MCLauncherApp
import com.mclauncher.core.download.DownloadManager
import com.mclauncher.data.remote.api.FabricApi
import com.mclauncher.domain.models.DownloadTask
import com.mclauncher.domain.models.DownloadType
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FabricInstaller @Inject constructor(
    private val context: Context,
    private val downloadManager: DownloadManager
) {
    private val moshi = Moshi.Builder().build()
    private val fabricApi: FabricApi by lazy {
        Retrofit.Builder()
            .baseUrl(FabricApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FabricApi::class.java)
    }

    private val versionsDir = MCLauncherApp.versionsDirectory
    private val librariesDir = MCLauncherApp.librariesDirectory

    suspend fun install(loaderVersion: String, minecraftVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Installing Fabric $loaderVersion for Minecraft $minecraftVersion")

            // Get version JSON from Fabric API
            val response = fabricApi.getVersionJson(minecraftVersion, loaderVersion)
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.failure(
                    Exception("Failed to get Fabric version info: ${response.code()}")
                )
            }

            val versionJson = response.body()!!
            val versionId = versionJson.id

            // Create version directory
            val versionDir = File(versionsDir, versionId)
            versionDir.mkdirs()

            // Save version JSON
            val jsonFile = File(versionDir, "$versionId.json")
            val adapter = moshi.adapter(com.mclauncher.data.remote.api.FabricVersionJson::class.java)
            jsonFile.writeText(adapter.toJson(versionJson))

            // Download required libraries
            val downloadTasks = mutableListOf<DownloadTask>()

            versionJson.libraries.forEach { library ->
                val libraryPath = mavenNameToPath(library.name)
                val libraryFile = File(librariesDir, libraryPath)
                
                if (!libraryFile.exists()) {
                    val url = (library.url ?: FabricApi.MAVEN_URL) + libraryPath
                    downloadTasks.add(
                        DownloadTask(
                            url = url,
                            destination = libraryFile,
                            type = DownloadType.LIBRARY
                        )
                    )
                }
            }

            if (downloadTasks.isNotEmpty()) {
                downloadManager.queueDownloads(downloadTasks)
                
                var hasError = false
                downloadManager.startDownloads().collect { progress ->
                    if (progress.error != null) {
                        hasError = true
                    }
                }

                if (hasError) {
                    return@withContext Result.failure(Exception("Failed to download Fabric libraries"))
                }
            }

            Timber.d("Fabric $loaderVersion installed successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Fabric installation failed")
            Result.failure(e)
        }
    }

    suspend fun isInstalled(loaderVersion: String, minecraftVersion: String): Boolean = withContext(Dispatchers.IO) {
        val versionId = "fabric-loader-$loaderVersion-$minecraftVersion"
        val versionDir = File(versionsDir, versionId)
        val jsonFile = File(versionDir, "$versionId.json")
        jsonFile.exists()
    }

    suspend fun getAvailableVersions(minecraftVersion: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = fabricApi.getLoadersForGameVersion(minecraftVersion)
            if (response.isSuccessful && response.body() != null) {
                val versions = response.body()!!
                    .map { it.loader.version }
                    .take(20) // Limit to recent versions
                Result.success(versions)
            } else {
                Result.failure(Exception("Failed to fetch Fabric versions: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Fabric versions")
            Result.failure(e)
        }
    }

    suspend fun getSupportedMinecraftVersions(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = fabricApi.getGameVersions()
            if (response.isSuccessful && response.body() != null) {
                val versions = response.body()!!
                    .filter { it.stable }
                    .map { it.version }
                Result.success(versions)
            } else {
                Result.failure(Exception("Failed to fetch game versions: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uninstall(loaderVersion: String, minecraftVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val versionId = "fabric-loader-$loaderVersion-$minecraftVersion"
            val versionDir = File(versionsDir, versionId)
            
            if (versionDir.exists()) {
                versionDir.deleteRecursively()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mavenNameToPath(name: String): String {
        val parts = name.split(":")
        if (parts.size < 3) return name

        val group = parts[0].replace(".", "/")
        val artifact = parts[1]
        val version = parts[2]
        val classifier = if (parts.size > 3) "-${parts[3]}" else ""

        return "$group/$artifact/$version/$artifact-$version$classifier.jar"
    }
}
