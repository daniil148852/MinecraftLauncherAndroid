package com.mclauncher.core.modloader

import android.content.Context
import com.mclauncher.MCLauncherApp
import com.mclauncher.core.download.DownloadManager
import com.mclauncher.data.remote.api.ForgeApi
import com.mclauncher.domain.models.DownloadTask
import com.mclauncher.domain.models.DownloadType
import com.mclauncher.utils.FileUtils
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForgeInstaller @Inject constructor(
    private val context: Context,
    private val downloadManager: DownloadManager
) {
    private val moshi = Moshi.Builder().build()
    private val forgeApi: ForgeApi by lazy {
        Retrofit.Builder()
            .baseUrl(ForgeApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ForgeApi::class.java)
    }

    private val versionsDir = MCLauncherApp.versionsDirectory
    private val librariesDir = MCLauncherApp.librariesDirectory

    suspend fun install(forgeVersion: String, minecraftVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Installing Forge $forgeVersion for Minecraft $minecraftVersion")

            val fullVersion = "$minecraftVersion-$forgeVersion"
            val versionId = "$minecraftVersion-forge-$forgeVersion"

            // Download Forge installer
            val installerUrl = ForgeApi.getInstallerUrl(minecraftVersion, forgeVersion)
            val installerFile = File(context.cacheDir, "forge-$fullVersion-installer.jar")

            val downloadResult = downloadManager.downloadSingleFile(
                url = installerUrl,
                destination = installerFile,
                type = DownloadType.MOD_LOADER
            )

            if (downloadResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to download Forge installer: ${downloadResult.exceptionOrNull()?.message}")
                )
            }

            // Extract and process installer
            val installResult = processForgeInstaller(installerFile, versionId, minecraftVersion, forgeVersion)
            
            // Clean up
            installerFile.delete()

            installResult

        } catch (e: Exception) {
            Timber.e(e, "Forge installation failed")
            Result.failure(e)
        }
    }

    private suspend fun processForgeInstaller(
        installerFile: File,
        versionId: String,
        minecraftVersion: String,
        forgeVersion: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ZipFile(installerFile).use { zip ->
                // Check for modern or legacy installer format
                val installProfileEntry = zip.getEntry("install_profile.json")
                val versionJsonEntry = zip.getEntry("version.json")

                if (installProfileEntry == null) {
                    return@withContext Result.failure(Exception("Invalid Forge installer: missing install_profile.json"))
                }

                // Create version directory
                val versionDir = File(versionsDir, versionId)
                versionDir.mkdirs()

                // Extract version.json
                if (versionJsonEntry != null) {
                    val versionJson = zip.getInputStream(versionJsonEntry).bufferedReader().readText()
                    
                    // Modify version JSON to use correct ID
                    val modifiedJson = modifyVersionJson(versionJson, versionId)
                    File(versionDir, "$versionId.json").writeText(modifiedJson)
                }

                // Parse install profile
                val installProfileJson = zip.getInputStream(installProfileEntry).bufferedReader().readText()
                val installProfile = parseInstallProfile(installProfileJson)

                // Download required libraries
                val downloadTasks = mutableListOf<DownloadTask>()

                installProfile.libraries.forEach { library ->
                    val artifact = library.downloads?.artifact
                    if (artifact != null) {
                        val libraryFile = File(librariesDir, artifact.path)
                        if (!libraryFile.exists()) {
                            downloadTasks.add(
                                DownloadTask(
                                    url = artifact.url,
                                    destination = libraryFile,
                                    sha1 = artifact.sha1,
                                    size = artifact.size,
                                    type = DownloadType.LIBRARY
                                )
                            )
                        }
                    } else {
                        // Handle libraries without download info
                        val path = mavenNameToPath(library.name)
                        val libraryFile = File(librariesDir, path)
                        if (!libraryFile.exists()) {
                            val url = (library.url ?: ForgeApi.MAVEN_URL) + path
                            downloadTasks.add(
                                DownloadTask(
                                    url = url,
                                    destination = libraryFile,
                                    type = DownloadType.LIBRARY
                                )
                            )
                        }
                    }
                }

                // Extract libraries from installer jar
                zip.entries().asSequence()
                    .filter { it.name.startsWith("maven/") && it.name.endsWith(".jar") }
                    .forEach { entry ->
                        val relativePath = entry.name.removePrefix("maven/")
                        val destFile = File(librariesDir, relativePath)
                        if (!destFile.exists()) {
                            destFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }

                // Download remaining libraries
                if (downloadTasks.isNotEmpty()) {
                    downloadManager.queueDownloads(downloadTasks)
                    
                    var hasError = false
                    downloadManager.startDownloads().collect { progress ->
                        if (progress.error != null) {
                            hasError = true
                        }
                    }

                    if (hasError) {
                        return@withContext Result.failure(Exception("Failed to download Forge libraries"))
                    }
                }

                // Run processors if needed (for modern Forge)
                if (installProfile.processors.isNotEmpty()) {
                    // Modern Forge requires running processors
                    // This is complex and requires running Java code
                    Timber.w("Forge processors not implemented - manual processing may be required")
                }
            }

            Timber.d("Forge $forgeVersion installed successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to process Forge installer")
            Result.failure(e)
        }
    }

    private fun modifyVersionJson(json: String, newId: String): String {
        // Simple string replacement for ID
        return json.replace(Regex(""""id"\s*:\s*"[^"]+""""), """"id": "$newId"""")
    }

    private fun parseInstallProfile(json: String): ForgeInstallProfile {
        // Simplified parsing
        val adapter = moshi.adapter(ForgeInstallProfile::class.java)
        return adapter.fromJson(json) ?: ForgeInstallProfile(emptyList(), emptyList())
    }

    suspend fun isInstalled(forgeVersion: String, minecraftVersion: String): Boolean = withContext(Dispatchers.IO) {
        val versionId = "$minecraftVersion-forge-$forgeVersion"
        val versionDir = File(versionsDir, versionId)
        val jsonFile = File(versionDir, "$versionId.json")
        jsonFile.exists()
    }

    suspend fun getAvailableVersions(minecraftVersion: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = forgeApi.getForgeVersions()
            if (response.isSuccessful && response.body() != null) {
                val allVersions = response.body()!!
                val versionsForMc = allVersions[minecraftVersion] ?: emptyList()
                
                // Return most recent versions first
                val sorted = versionsForMc
                    .map { it.removePrefix("$minecraftVersion-") }
                    .sortedDescending()
                    .take(20)
                
                Result.success(sorted)
            } else {
                Result.failure(Exception("Failed to fetch Forge versions: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Forge versions")
            Result.failure(e)
        }
    }

    suspend fun getRecommendedVersion(minecraftVersion: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val response = forgeApi.getForgePromotions()
            if (response.isSuccessful && response.body() != null) {
                val promos = response.body()!!.promos
                val recommended = promos["$minecraftVersion-recommended"]
                    ?: promos["$minecraftVersion-latest"]
                Result.success(recommended)
            } else {
                Result.failure(Exception("Failed to fetch promotions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uninstall(forgeVersion: String, minecraftVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val versionId = "$minecraftVersion-forge-$forgeVersion"
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

// Simplified install profile data class
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ForgeInstallProfile(
    @com.squareup.moshi.Json(name = "libraries")
    val libraries: List<com.mclauncher.data.remote.api.ForgeLibrary>,
    @com.squareup.moshi.Json(name = "processors")
    val processors: List<ForgeProcessor> = emptyList()
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ForgeProcessor(
    @com.squareup.moshi.Json(name = "jar")
    val jar: String,
    @com.squareup.moshi.Json(name = "classpath")
    val classpath: List<String> = emptyList(),
    @com.squareup.moshi.Json(name = "args")
    val args: List<String> = emptyList(),
    @com.squareup.moshi.Json(name = "sides")
    val sides: List<String>? = null
)
