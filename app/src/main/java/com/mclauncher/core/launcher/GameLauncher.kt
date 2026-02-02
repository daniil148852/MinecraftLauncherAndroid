package com.mclauncher.core.launcher

import android.content.Context
import android.content.Intent
import com.mclauncher.MCLauncherApp
import com.mclauncher.core.download.DownloadManager
import com.mclauncher.core.modloader.ModLoaderManager
import com.mclauncher.core.runtime.JREManager
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.remote.models.VersionDetails
import com.mclauncher.data.repository.AccountRepository
import com.mclauncher.data.repository.ProfileRepository
import com.mclauncher.data.repository.VersionRepository
import com.mclauncher.domain.models.Account
import com.mclauncher.domain.models.LaunchConfig
import com.mclauncher.domain.models.ModLoader
import com.mclauncher.domain.models.Profile
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class LaunchState {
    data class Preparing(val progress: Float, val message: String) : LaunchState()
    object Launching : LaunchState()
    data class Running(val pid: Int) : LaunchState()
    data class Stopped(val exitCode: Int, val playTimeMs: Long) : LaunchState()
    data class Error(val message: String, val exception: Throwable? = null) : LaunchState()
}

@Singleton
class GameLauncher @Inject constructor(
    private val context: Context,
    private val versionRepository: VersionRepository,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    private val downloadManager: DownloadManager,
    private val jreManager: JREManager,
    private val modLoaderManager: ModLoaderManager,
    private val preferencesManager: PreferencesManager
) {
    private val moshi = Moshi.Builder().build()
    private var currentProcess: Process? = null
    private var launchStartTime: Long = 0
    private var isCancelled = false

    fun launch(profile: Profile, account: Account): Flow<LaunchState> = flow {
        isCancelled = false
        launchStartTime = System.currentTimeMillis()

        try {
            emit(LaunchState.Preparing(0.1f, "Checking Java runtime..."))
            
            // Check Java
            if (!jreManager.isJavaInstalled()) {
                emit(LaunchState.Preparing(0.15f, "Downloading Java runtime..."))
                jreManager.downloadJRE().collect { state ->
                    when (state) {
                        is JREManager.DownloadState.Downloading -> {
                            emit(LaunchState.Preparing(0.15f + state.progress * 0.25f, "Downloading Java: ${(state.progress * 100).toInt()}%"))
                        }
                        is JREManager.DownloadState.Failed -> {
                            throw Exception("Failed to download Java: ${state.error}")
                        }
                        else -> {}
                    }
                }
            }

            if (isCancelled) return@flow

            emit(LaunchState.Preparing(0.4f, "Loading version details..."))
            
            // Get version details
            val version = versionRepository.getVersionById(profile.versionId)
                ?: throw Exception("Version ${profile.versionId} not found")

            val versionDir = File(MCLauncherApp.versionsDirectory, profile.versionId)
            val versionJsonFile = File(versionDir, "${profile.versionId}.json")
            
            if (!versionJsonFile.exists()) {
                throw Exception("Version JSON not found. Please redownload the version.")
            }

            val versionDetails = parseVersionJson(versionJsonFile)

            if (isCancelled) return@flow

            emit(LaunchState.Preparing(0.5f, "Building classpath..."))

            // Build launch configuration
            val launchConfig = buildLaunchConfig(profile, account, versionDetails)

            if (isCancelled) return@flow

            emit(LaunchState.Preparing(0.6f, "Extracting natives..."))

            // Extract natives
            extractNatives(versionDetails, launchConfig.nativesDirectory)

            if (isCancelled) return@flow

            emit(LaunchState.Preparing(0.8f, "Preparing launch arguments..."))

            // Build command
            val command = buildLaunchCommand(launchConfig, versionDetails)

            if (isCancelled) return@flow

            emit(LaunchState.Preparing(0.9f, "Starting Minecraft..."))

            // Log command for debugging
            Timber.d("Launch command: ${command.joinToString(" ")}")

            emit(LaunchState.Launching)

            // Start process
            val processBuilder = ProcessBuilder(command)
                .directory(launchConfig.gameDirectory)
                .redirectErrorStream(true)

            // Set environment variables
            val environment = processBuilder.environment()
            environment["JAVA_HOME"] = jreManager.getJavaHome()
            environment["LD_LIBRARY_PATH"] = launchConfig.nativesDirectory.absolutePath

            currentProcess = processBuilder.start()

            emit(LaunchState.Running(currentProcess!!.pid().toInt()))

            // Wait for process to finish
            val exitCode = currentProcess!!.waitFor()
            val playTime = System.currentTimeMillis() - launchStartTime

            Timber.d("Minecraft exited with code: $exitCode after ${playTime}ms")

            emit(LaunchState.Stopped(exitCode, playTime))

        } catch (e: Exception) {
            Timber.e(e, "Launch failed")
            emit(LaunchState.Error(e.message ?: "Unknown error", e))
        } finally {
            currentProcess = null
        }
    }.flowOn(Dispatchers.IO)

    fun cancel() {
        isCancelled = true
        currentProcess?.destroyForcibly()
        currentProcess = null
    }

    private fun parseVersionJson(file: File): VersionDetails {
        val adapter = moshi.adapter(VersionDetails::class.java)
        return adapter.fromJson(file.readText())
            ?: throw Exception("Failed to parse version JSON")
    }

    private suspend fun buildLaunchConfig(
        profile: Profile,
        account: Account,
        versionDetails: VersionDetails
    ): LaunchConfig = withContext(Dispatchers.IO) {
        val gameDir = profile.gameDirectory ?: MCLauncherApp.gameDirectory
        val nativesDir = File(MCLauncherApp.nativesDirectory, profile.versionId)
        nativesDir.mkdirs()

        // Build classpath
        val classpathEntries = mutableListOf<File>()
        
        // Add client JAR
        val clientJar = File(MCLauncherApp.versionsDirectory, "${profile.versionId}/${profile.versionId}.jar")
        if (clientJar.exists()) {
            classpathEntries.add(clientJar)
        }

        // Add libraries
        versionDetails.libraries.forEach { library ->
            if (shouldIncludeLibrary(library)) {
                library.downloads?.artifact?.let { artifact ->
                    val libFile = File(MCLauncherApp.librariesDirectory, artifact.path)
                    if (libFile.exists()) {
                        classpathEntries.add(libFile)
                    }
                }
            }
        }

        // Add mod loader libraries if applicable
        if (profile.modLoader != ModLoader.NONE) {
            val modLoaderLibs = modLoaderManager.getModLoaderLibraries(
                profile.modLoader,
                profile.modLoaderVersion ?: "",
                profile.versionId
            )
            classpathEntries.addAll(modLoaderLibs)
        }

        LaunchConfig(
            profile = profile,
            account = account,
            version = versionRepository.getVersionById(profile.versionId)!!,
            javaPath = jreManager.getJavaExecutable(),
            gameDirectory = gameDir,
            nativesDirectory = nativesDir,
            assetsDirectory = MCLauncherApp.assetsDirectory,
            librariesDirectory = MCLauncherApp.librariesDirectory,
            classpathEntries = classpathEntries,
            jvmArguments = buildJvmArguments(profile, versionDetails),
            gameArguments = buildGameArguments(profile, account, versionDetails),
            mainClass = getMainClass(profile, versionDetails),
            windowWidth = profile.gameSettings.width,
            windowHeight = profile.gameSettings.height,
            fullscreen = profile.gameSettings.fullscreen
        )
    }

    private fun buildJvmArguments(profile: Profile, versionDetails: VersionDetails): List<String> {
        val args = mutableListOf<String>()

        // Memory settings
        args.add("-Xms${profile.gameSettings.ramMb / 2}M")
        args.add("-Xmx${profile.gameSettings.ramMb}M")

        // Custom JVM arguments from profile
        args.addAll(profile.jvmArguments)

        // Version-specific JVM arguments
        versionDetails.arguments?.jvm?.forEach { arg ->
            when (arg) {
                is String -> args.add(arg)
                // Complex arguments with rules would be handled here
            }
        }

        return args
    }

    private fun buildGameArguments(
        profile: Profile,
        account: Account,
        versionDetails: VersionDetails
    ): List<String> {
        val args = mutableListOf<String>()

        // Handle new argument format
        versionDetails.arguments?.game?.forEach { arg ->
            when (arg) {
                is String -> args.add(arg)
            }
        }

        // Handle legacy argument format
        versionDetails.minecraftArguments?.let { legacyArgs ->
            args.addAll(legacyArgs.split(" "))
        }

        // Custom game arguments
        args.addAll(profile.gameArguments)

        // Replace placeholders
        return args.map { arg ->
            replacePlaceholders(arg, profile, account, versionDetails)
        }
    }

    private fun replacePlaceholders(
        arg: String,
        profile: Profile,
        account: Account,
        versionDetails: VersionDetails
    ): String {
        return arg
            .replace("\${auth_player_name}", account.username)
            .replace("\${version_name}", profile.versionId)
            .replace("\${game_directory}", profile.gameDirectory?.absolutePath ?: MCLauncherApp.gameDirectory.absolutePath)
            .replace("\${assets_root}", MCLauncherApp.assetsDirectory.absolutePath)
            .replace("\${assets_index_name}", versionDetails.assetIndex.id)
            .replace("\${auth_uuid}", account.uuid.replace("-", ""))
            .replace("\${auth_access_token}", account.accessToken ?: "0")
            .replace("\${user_type}", if (account.isOffline) "legacy" else "msa")
            .replace("\${version_type}", versionDetails.type)
            .replace("\${user_properties}", "{}")
            .replace("\${resolution_width}", profile.gameSettings.width.toString())
            .replace("\${resolution_height}", profile.gameSettings.height.toString())
            .replace("\${natives_directory}", File(MCLauncherApp.nativesDirectory, profile.versionId).absolutePath)
            .replace("\${launcher_name}", "MCLauncher")
            .replace("\${launcher_version}", "1.0.0")
            .replace("\${classpath}", "") // Handled separately
    }

    private fun getMainClass(profile: Profile, versionDetails: VersionDetails): String {
        // Use mod loader main class if applicable
        if (profile.modLoader != ModLoader.NONE) {
            val modLoaderMainClass = modLoaderManager.getMainClass(
                profile.modLoader,
                profile.modLoaderVersion ?: "",
                profile.versionId
            )
            if (modLoaderMainClass != null) {
                return modLoaderMainClass
            }
        }
        return versionDetails.mainClass
    }

    private fun buildLaunchCommand(config: LaunchConfig, versionDetails: VersionDetails): List<String> {
        val command = mutableListOf<String>()

        // Java executable
        command.add(config.javaPath)

        // JVM arguments
        command.addAll(config.jvmArguments)

        // Natives path
        command.add("-Djava.library.path=${config.nativesDirectory.absolutePath}")

        // Classpath
        command.add("-cp")
        command.add(config.classpathEntries.joinToString(File.pathSeparator) { it.absolutePath })

        // Main class
        command.add(config.mainClass)

        // Game arguments
        command.addAll(config.gameArguments)

        // Resolution
        if (!config.fullscreen) {
            command.add("--width")
            command.add(config.windowWidth.toString())
            command.add("--height")
            command.add(config.windowHeight.toString())
        } else {
            command.add("--fullscreen")
        }

        return command
    }

    private fun extractNatives(versionDetails: VersionDetails, nativesDir: File) {
        nativesDir.mkdirs()

        versionDetails.libraries.forEach { library ->
            if (!shouldIncludeLibrary(library)) return@forEach

            // Check for natives
            library.natives?.get("linux")?.let { nativeKey ->
                library.downloads?.classifiers?.get(nativeKey)?.let { nativeArtifact ->
                    val nativeFile = File(MCLauncherApp.librariesDirectory, nativeArtifact.path)
                    if (nativeFile.exists()) {
                        extractNativeJar(nativeFile, nativesDir, library.extract?.exclude)
                    }
                }
            }
        }
    }

    private fun extractNativeJar(jarFile: File, destDir: File, exclude: List<String>?) {
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                zip.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory &&
                        (exclude == null || exclude.none { entry.name.startsWith(it) }) &&
                        (entry.name.endsWith(".so") || entry.name.endsWith(".dylib") || entry.name.endsWith(".dll"))
                    }
                    .forEach { entry ->
                        val destFile = File(destDir, entry.name.substringAfterLast("/"))
                        if (!destFile.exists()) {
                            zip.getInputStream(entry).use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Timber.d("Extracted native: ${destFile.name}")
                        }
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract natives from ${jarFile.name}")
        }
    }

    private fun shouldIncludeLibrary(library: com.mclauncher.data.remote.models.Library): Boolean {
        val rules = library.rules ?: return true

        var dominated = false
        var dominatedValue = false

        for (rule in rules) {
            val osMatch = rule.os?.let { os ->
                // For Android, treat as Linux
                when {
                    os.name == "linux" -> true
                    os.name == "osx" || os.name == "windows" -> false
                    else -> true
                }
            } ?: true

            if (osMatch) {
                dominated = true
                dominatedValue = rule.action == "allow"
            }
        }

        return if (dominated) dominatedValue else true
    }
}
