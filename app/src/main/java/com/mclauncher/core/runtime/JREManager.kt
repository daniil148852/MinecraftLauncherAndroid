package com.mclauncher.core.runtime

import android.content.Context
import android.os.Build
import com.mclauncher.MCLauncherApp
import com.mclauncher.core.download.DownloadManager
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.domain.models.DownloadTask
import com.mclauncher.domain.models.DownloadType
import com.mclauncher.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JREManager @Inject constructor(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val preferencesManager: PreferencesManager
) {
    private val runtimeDir = MCLauncherApp.runtimeDirectory
    private val javaDir = File(runtimeDir, "java")

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        object Extracting : DownloadState()
        object Completed : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }

    companion object {
        // Adoptium (Eclipse Temurin) JRE download URLs
        private const val ADOPTIUM_API = "https://api.adoptium.net/v3/binary/latest"
        
        // Java versions for different Minecraft versions
        const val JAVA_8 = 8
        const val JAVA_17 = 17
        const val JAVA_21 = 21
        
        fun getRequiredJavaVersion(minecraftVersion: String): Int {
            return when {
                // 1.20.5+ requires Java 21
                compareVersions(minecraftVersion, "1.20.5") >= 0 -> JAVA_21
                // 1.18+ requires Java 17
                compareVersions(minecraftVersion, "1.18") >= 0 -> JAVA_17
                // Older versions use Java 8
                else -> JAVA_8
            }
        }

        private fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
            
            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }
    }

    suspend fun isJavaInstalled(version: Int = JAVA_17): Boolean = withContext(Dispatchers.IO) {
        val javaHome = getJavaHome(version)
        val javaBinary = File(javaHome, "bin/java")
        javaBinary.exists() && javaBinary.canExecute()
    }

    fun getJavaHome(version: Int = JAVA_17): String {
        return File(javaDir, "jdk-$version").absolutePath
    }

    fun getJavaExecutable(version: Int = JAVA_17): String {
        return File(getJavaHome(version), "bin/java").absolutePath
    }

    suspend fun getJavaVersion(): String = withContext(Dispatchers.IO) {
        try {
            val javaExe = getJavaExecutable()
            if (!File(javaExe).exists()) {
                return@withContext "Not installed"
            }

            val process = ProcessBuilder(javaExe, "-version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // Parse version from output
            val versionRegex = Regex("""version "([^"]+)"""")
            val match = versionRegex.find(output)
            match?.groupValues?.get(1) ?: "Unknown"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Java version")
            "Error"
        }
    }

    fun downloadJRE(version: Int = JAVA_17): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        try {
            // Determine architecture
            val arch = getArchitecture()
            if (arch == null) {
                emit(DownloadState.Failed("Unsupported architecture"))
                return@flow
            }

            // Build download URL
            val downloadUrl = buildDownloadUrl(version, arch)
            Timber.d("Downloading JRE from: $downloadUrl")

            // Create temp file for download
            val tempFile = File(context.cacheDir, "jre-$version-$arch.tar.gz")
            val destDir = File(javaDir, "jdk-$version")

            // Download
            emit(DownloadState.Downloading(0f))

            val downloadResult = downloadManager.downloadSingleFile(
                url = downloadUrl,
                destination = tempFile,
                type = DownloadType.JAVA_RUNTIME
            )

            if (downloadResult.isFailure) {
                emit(DownloadState.Failed("Download failed: ${downloadResult.exceptionOrNull()?.message}"))
                return@flow
            }

            emit(DownloadState.Downloading(1f))
            emit(DownloadState.Extracting)

            // Extract
            destDir.deleteRecursively()
            destDir.mkdirs()

            val extractResult = extractTarGz(tempFile, destDir)
            if (extractResult.isFailure) {
                emit(DownloadState.Failed("Extraction failed: ${extractResult.exceptionOrNull()?.message}"))
                return@flow
            }

            // Find and move extracted contents (usually in a subdirectory)
            val extractedContents = destDir.listFiles()?.firstOrNull { it.isDirectory }
            if (extractedContents != null && extractedContents.name != destDir.name) {
                extractedContents.listFiles()?.forEach { file ->
                    file.renameTo(File(destDir, file.name))
                }
                extractedContents.delete()
            }

            // Make java executable
            val javaBinary = File(destDir, "bin/java")
            if (javaBinary.exists()) {
                javaBinary.setExecutable(true)
            }

            // Clean up
            tempFile.delete()

            // Verify installation
            if (!isJavaInstalled(version)) {
                emit(DownloadState.Failed("Installation verification failed"))
                return@flow
            }

            Timber.d("JRE $version installed successfully")
            emit(DownloadState.Completed)

        } catch (e: Exception) {
            Timber.e(e, "JRE download failed")
            emit(DownloadState.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    private fun getArchitecture(): String? {
        val supportedAbis = Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "aarch64"
            supportedAbis.contains("armeabi-v7a") -> "arm"
            supportedAbis.contains("x86_64") -> "x64"
            supportedAbis.contains("x86") -> "x86-32"
            else -> null
        }
    }

    private fun buildDownloadUrl(version: Int, arch: String): String {
        // Using Adoptium API
        return "$ADOPTIUM_API/$version/ga/linux/$arch/jre/hotspot/normal/eclipse?" +
                "project=jdk"
    }

    private suspend fun extractTarGz(tarGzFile: File, destDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Use Android's built-in tar extraction or manual extraction
            val process = ProcessBuilder(
                "tar", "-xzf", tarGzFile.absolutePath, "-C", destDir.absolutePath
            ).start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                // Fallback to manual extraction
                return@withContext extractTarGzManual(tarGzFile, destDir)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            // Fallback to manual extraction
            extractTarGzManual(tarGzFile, destDir)
        }
    }

    private fun extractTarGzManual(tarGzFile: File, destDir: File): Result<Unit> {
        return try {
            java.util.zip.GZIPInputStream(tarGzFile.inputStream()).use { gzis ->
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzis).use { tais ->
                    var entry = tais.nextTarEntry
                    while (entry != null) {
                        val destFile = File(destDir, entry.name)
                        
                        // Security check
                        if (!destFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                            throw SecurityException("Tar entry outside target directory")
                        }

                        if (entry.isDirectory) {
                            destFile.mkdirs()
                        } else {
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { output ->
                                tais.copyTo(output)
                            }
                            
                            // Preserve executable permission
                            if (entry.mode and 0b001_000_000 != 0) {
                                destFile.setExecutable(true)
                            }
                        }
                        entry = tais.nextTarEntry
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Manual tar extraction failed")
            Result.failure(e)
        }
    }

    suspend fun deleteJRE(version: Int = JAVA_17) = withContext(Dispatchers.IO) {
        val javaHome = File(getJavaHome(version))
        if (javaHome.exists()) {
            FileUtils.deleteRecursively(javaHome)
        }
    }

    suspend fun getInstalledJavaVersions(): List<Int> = withContext(Dispatchers.IO) {
        val versions = mutableListOf<Int>()
        if (isJavaInstalled(JAVA_8)) versions.add(JAVA_8)
        if (isJavaInstalled(JAVA_17)) versions.add(JAVA_17)
        if (isJavaInstalled(JAVA_21)) versions.add(JAVA_21)
        versions
    }

    suspend fun ensureJavaForVersion(minecraftVersion: String): Result<String> = withContext(Dispatchers.IO) {
        val requiredVersion = getRequiredJavaVersion(minecraftVersion)
        
        if (isJavaInstalled(requiredVersion)) {
            return@withContext Result.success(getJavaExecutable(requiredVersion))
        }

        // Try to download
        var downloadResult: Result<String> = Result.failure(Exception("Download not started"))
        
        downloadJRE(requiredVersion).collect { state ->
            when (state) {
                is DownloadState.Completed -> {
                    downloadResult = Result.success(getJavaExecutable(requiredVersion))
                }
                is DownloadState.Failed -> {
                    downloadResult = Result.failure(Exception(state.error))
                }
                else -> {}
            }
        }

        downloadResult
    }
}
