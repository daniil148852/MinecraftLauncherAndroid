package com.mclauncher.core.download

import android.content.Context
import com.mclauncher.MCLauncherApp
import com.mclauncher.data.remote.api.MojangApi
import com.mclauncher.data.remote.models.*
import com.mclauncher.data.repository.VersionRepository
import com.mclauncher.domain.models.DownloadTask
import com.mclauncher.domain.models.DownloadType
import com.mclauncher.utils.Constants
import com.mclauncher.utils.FileUtils
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionDownloader @Inject constructor(
    private val context: Context,
    private val mojangApi: MojangApi,
    private val downloadManager: DownloadManager,
    private val versionRepository: VersionRepository,
    private val moshi: Moshi
) {
    private val versionsDir = MCLauncherApp.versionsDirectory
    private val librariesDir = MCLauncherApp.librariesDirectory
    private val assetsDir = MCLauncherApp.assetsDirectory

    sealed class DownloadState {
        object Idle : DownloadState()
        data class FetchingManifest(val message: String) : DownloadState()
        data class PreparingDownloads(val message: String) : DownloadState()
        data class Downloading(val progress: Float, val currentFile: String, val downloaded: Int, val total: Int) : DownloadState()
        data class Extracting(val message: String) : DownloadState()
        data class Completed(val versionId: String) : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }

    fun downloadVersion(versionId: String): Flow<DownloadState> = flow {
        emit(DownloadState.FetchingManifest("Fetching version details..."))

        try {
            // Get version details
            val detailsResult = versionRepository.fetchVersionDetails(versionId)
            if (detailsResult.isFailure) {
                emit(DownloadState.Failed("Failed to fetch version details: ${detailsResult.exceptionOrNull()?.message}"))
                return@flow
            }

            val details = detailsResult.getOrThrow()
            
            // Handle inheritsFrom (for modded versions)
            val resolvedDetails = resolveInheritance(details)
            
            emit(DownloadState.PreparingDownloads("Preparing download list..."))

            val downloadTasks = mutableListOf<DownloadTask>()

            // 1. Client JAR
            val clientJarTask = prepareClientJarDownload(versionId, resolvedDetails)
            downloadTasks.add(clientJarTask)

            // 2. Libraries
            val libraryTasks = prepareLibraryDownloads(resolvedDetails)
            downloadTasks.addAll(libraryTasks)

            // 3. Assets
            val assetTasks = prepareAssetDownloads(resolvedDetails)
            downloadTasks.addAll(assetTasks)

            // 4. Save version JSON
            saveVersionJson(versionId, resolvedDetails)

            val totalTasks = downloadTasks.size
            Timber.d("Total files to download: $totalTasks")

            emit(DownloadState.PreparingDownloads("Preparing to download $totalTasks files..."))

            // Queue all downloads
            downloadManager.queueDownloads(downloadTasks)

            // Start downloads and track progress
            downloadManager.startDownloads().collect { progress ->
                if (progress.error != null) {
                    emit(DownloadState.Failed(progress.error))
                    return@collect
                }

                if (progress.isComplete) {
                    // Mark version as installed
                    versionRepository.markVersionInstalled(
                        versionId,
                        File(versionsDir, versionId).absolutePath
                    )
                    emit(DownloadState.Completed(versionId))
                } else {
                    val currentFile = progress.currentTasks.firstOrNull()?.destination?.name ?: ""
                    emit(DownloadState.Downloading(
                        progress = progress.progress,
                        currentFile = currentFile,
                        downloaded = progress.completed,
                        total = progress.total
                    ))
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to download version $versionId")
            emit(DownloadState.Failed(e.message ?: "Unknown error"))
        }
    }

    private suspend fun resolveInheritance(details: VersionDetails): VersionDetails {
        if (details.inheritsFrom == null) {
            return details
        }

        // Fetch parent version
        val parentResult = versionRepository.fetchVersionDetails(details.inheritsFrom)
        if (parentResult.isFailure) {
            throw Exception("Failed to fetch parent version: ${details.inheritsFrom}")
        }

        val parent = parentResult.getOrThrow()
        val resolvedParent = resolveInheritance(parent)

        // Merge libraries
        val mergedLibraries = (resolvedParent.libraries + details.libraries).distinctBy { it.name }

        return details.copy(
            mainClass = details.mainClass.ifEmpty { resolvedParent.mainClass },
            libraries = mergedLibraries,
            downloads = details.downloads,
            assetIndex = details.assetIndex,
            assets = details.assets.ifEmpty { resolvedParent.assets }
        )
    }

    private fun prepareClientJarDownload(versionId: String, details: VersionDetails): DownloadTask {
        val versionDir = File(versionsDir, versionId)
        versionDir.mkdirs()

        val clientJar = File(versionDir, "$versionId.jar")

        return DownloadTask(
            url = details.downloads.client.url,
            destination = clientJar,
            sha1 = details.downloads.client.sha1,
            size = details.downloads.client.size,
            type = DownloadType.CLIENT_JAR
        )
    }

    private fun prepareLibraryDownloads(details: VersionDetails): List<DownloadTask> {
        val tasks = mutableListOf<DownloadTask>()

        for (library in details.libraries) {
            if (!shouldIncludeLibrary(library)) {
                continue
            }

            // Handle artifact
            library.downloads?.artifact?.let { artifact ->
                val libFile = File(librariesDir, artifact.path)
                if (!libFile.exists()) {
                    tasks.add(DownloadTask(
                        url = artifact.url,
                        destination = libFile,
                        sha1 = artifact.sha1,
                        size = artifact.size,
                        type = DownloadType.LIBRARY
                    ))
                }
            }

            // Handle natives for Android (using linux-arm64 or linux natives as fallback)
            library.natives?.let { natives ->
                val nativeKey = natives["linux"] ?: natives["linux-arm64"]
                if (nativeKey != null) {
                    library.downloads?.classifiers?.get(nativeKey)?.let { nativeArtifact ->
                        val nativeFile = File(librariesDir, nativeArtifact.path)
                        if (!nativeFile.exists()) {
                            tasks.add(DownloadTask(
                                url = nativeArtifact.url,
                                destination = nativeFile,
                                sha1 = nativeArtifact.sha1,
                                size = nativeArtifact.size,
                                type = DownloadType.NATIVE
                            ))
                        }
                    }
                }
            }

            // Handle libraries without downloads section (older format)
            if (library.downloads == null && library.url != null) {
                val path = mavenNameToPath(library.name)
                val url = library.url + path
                val libFile = File(librariesDir, path)
                if (!libFile.exists()) {
                    tasks.add(DownloadTask(
                        url = url,
                        destination = libFile,
                        type = DownloadType.LIBRARY
                    ))
                }
            }
        }

        return tasks
    }

    private suspend fun prepareAssetDownloads(details: VersionDetails): List<DownloadTask> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<DownloadTask>()

        // Download asset index
        val indexDir = File(assetsDir, "indexes")
        indexDir.mkdirs()
        val indexFile = File(indexDir, "${details.assetIndex.id}.json")

        if (!indexFile.exists()) {
            val result = downloadManager.downloadSingleFile(
                url = details.assetIndex.url,
                destination = indexFile,
                sha1 = details.assetIndex.sha1
            )
            if (result.isFailure) {
                throw Exception("Failed to download asset index")
            }
        }

        // Parse asset index
        val assetIndex = try {
            val adapter = moshi.adapter(AssetIndex::class.java)
            adapter.fromJson(indexFile.readText())
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse asset index")
            null
        }

        assetIndex?.objects?.forEach { (name, asset) ->
            val hashPrefix = asset.hash.substring(0, 2)
            val assetFile = if (assetIndex.virtual || assetIndex.mapToResources) {
                File(assetsDir, "virtual/legacy/$name")
            } else {
                File(assetsDir, "objects/$hashPrefix/${asset.hash}")
            }

            if (!assetFile.exists()) {
                tasks.add(DownloadTask(
                    url = "${Constants.RESOURCES_URL}$hashPrefix/${asset.hash}",
                    destination = assetFile,
                    sha1 = asset.hash,
                    size = asset.size,
                    type = DownloadType.ASSET
                ))
            }
        }

        tasks
    }

    private fun saveVersionJson(versionId: String, details: VersionDetails) {
        val versionDir = File(versionsDir, versionId)
        versionDir.mkdirs()
        
        val jsonFile = File(versionDir, "$versionId.json")
        val adapter = moshi.adapter(VersionDetails::class.java)
        jsonFile.writeText(adapter.toJson(details))
    }

    private fun shouldIncludeLibrary(library: Library): Boolean {
        val rules = library.rules ?: return true

        var dominated = false
        var dominated_value = false

        for (rule in rules) {
            val osMatch = rule.os?.let { os ->
                // For Android, we treat it as Linux ARM
                when {
                    os.name == "linux" -> true
                    os.name == "osx" || os.name == "windows" -> false
                    else -> true
                }
            } ?: true

            if (osMatch) {
                dominated = true
                dominated_value = rule.action == "allow"
            }
        }

        return if (dominated) dominated_value else true
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

    suspend fun deleteVersion(versionId: String) = withContext(Dispatchers.IO) {
        val versionDir = File(versionsDir, versionId)
        if (versionDir.exists()) {
            FileUtils.deleteRecursively(versionDir)
        }
        versionRepository.markVersionUninstalled(versionId)
        Timber.d("Deleted version: $versionId")
    }

    suspend fun getVersionSize(versionId: String): Long = withContext(Dispatchers.IO) {
        val versionDir = File(versionsDir, versionId)
        if (versionDir.exists()) {
            FileUtils.getDirectorySize(versionDir)
        } else {
            0L
        }
    }
}
