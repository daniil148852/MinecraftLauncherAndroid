package com.mclauncher.core.modloader

import com.mclauncher.MCLauncherApp
import com.mclauncher.domain.models.ModLoader
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModLoaderManager @Inject constructor(
    private val fabricInstaller: FabricInstaller,
    private val forgeInstaller: ForgeInstaller
) {
    suspend fun installModLoader(
        modLoader: ModLoader,
        loaderVersion: String,
        minecraftVersion: String
    ): Result<Unit> {
        return when (modLoader) {
            ModLoader.FABRIC -> fabricInstaller.install(loaderVersion, minecraftVersion)
            ModLoader.FORGE -> forgeInstaller.install(loaderVersion, minecraftVersion)
            ModLoader.QUILT -> installQuilt(loaderVersion, minecraftVersion)
            ModLoader.NEOFORGE -> installNeoForge(loaderVersion, minecraftVersion)
            ModLoader.NONE -> Result.success(Unit)
        }
    }

    suspend fun isModLoaderInstalled(
        modLoader: ModLoader,
        loaderVersion: String,
        minecraftVersion: String
    ): Boolean {
        return when (modLoader) {
            ModLoader.FABRIC -> fabricInstaller.isInstalled(loaderVersion, minecraftVersion)
            ModLoader.FORGE -> forgeInstaller.isInstalled(loaderVersion, minecraftVersion)
            ModLoader.QUILT -> isQuiltInstalled(loaderVersion, minecraftVersion)
            ModLoader.NEOFORGE -> isNeoForgeInstalled(loaderVersion, minecraftVersion)
            ModLoader.NONE -> true
        }
    }

    suspend fun getAvailableVersions(
        modLoader: ModLoader,
        minecraftVersion: String
    ): Result<List<String>> {
        return when (modLoader) {
            ModLoader.FABRIC -> fabricInstaller.getAvailableVersions(minecraftVersion)
            ModLoader.FORGE -> forgeInstaller.getAvailableVersions(minecraftVersion)
            ModLoader.QUILT -> getQuiltVersions(minecraftVersion)
            ModLoader.NEOFORGE -> getNeoForgeVersions(minecraftVersion)
            ModLoader.NONE -> Result.success(emptyList())
        }
    }

    fun getMainClass(
        modLoader: ModLoader,
        loaderVersion: String,
        minecraftVersion: String
    ): String? {
        return when (modLoader) {
            ModLoader.FABRIC -> "net.fabricmc.loader.impl.launch.knot.KnotClient"
            ModLoader.FORGE -> getForgeMainClass(loaderVersion, minecraftVersion)
            ModLoader.QUILT -> "org.quiltmc.loader.impl.launch.knot.KnotClient"
            ModLoader.NEOFORGE -> "cpw.mods.bootstraplauncher.BootstrapLauncher"
            ModLoader.NONE -> null
        }
    }

    fun getModLoaderLibraries(
        modLoader: ModLoader,
        loaderVersion: String,
        minecraftVersion: String
    ): List<File> {
        val librariesDir = MCLauncherApp.librariesDirectory
        val libraries = mutableListOf<File>()

        when (modLoader) {
            ModLoader.FABRIC -> {
                // Fabric libraries
                val fabricDir = File(librariesDir, "net/fabricmc")
                if (fabricDir.exists()) {
                    fabricDir.walkTopDown()
                        .filter { it.isFile && it.extension == "jar" }
                        .forEach { libraries.add(it) }
                }
            }
            ModLoader.FORGE -> {
                // Forge libraries
                val forgeDir = File(librariesDir, "net/minecraftforge")
                if (forgeDir.exists()) {
                    forgeDir.walkTopDown()
                        .filter { it.isFile && it.extension == "jar" }
                        .forEach { libraries.add(it) }
                }
                // Also include cpw libraries for ModLauncher
                val cpwDir = File(librariesDir, "cpw/mods")
                if (cpwDir.exists()) {
                    cpwDir.walkTopDown()
                        .filter { it.isFile && it.extension == "jar" }
                        .forEach { libraries.add(it) }
                }
            }
            ModLoader.QUILT -> {
                val quiltDir = File(librariesDir, "org/quiltmc")
                if (quiltDir.exists()) {
                    quiltDir.walkTopDown()
                        .filter { it.isFile && it.extension == "jar" }
                        .forEach { libraries.add(it) }
                }
            }
            ModLoader.NEOFORGE -> {
                val neoforgeDir = File(librariesDir, "net/neoforged")
                if (neoforgeDir.exists()) {
                    neoforgeDir.walkTopDown()
                        .filter { it.isFile && it.extension == "jar" }
                        .forEach { libraries.add(it) }
                }
            }
            ModLoader.NONE -> {}
        }

        return libraries
    }

    private fun getForgeMainClass(loaderVersion: String, minecraftVersion: String): String {
        // Different main classes for different Forge versions
        val forgeMajor = loaderVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
        
        return when {
            // Modern Forge (1.13+) uses ModLauncher
            forgeMajor >= 25 -> "cpw.mods.modlauncher.Launcher"
            // Legacy Forge
            else -> "net.minecraft.launchwrapper.Launch"
        }
    }

    private suspend fun installQuilt(loaderVersion: String, minecraftVersion: String): Result<Unit> {
        // Quilt is similar to Fabric, would implement similarly
        Timber.w("Quilt installation not yet implemented")
        return Result.failure(Exception("Quilt installation not yet implemented"))
    }

    private suspend fun installNeoForge(loaderVersion: String, minecraftVersion: String): Result<Unit> {
        // NeoForge is a fork of Forge for 1.20.1+
        Timber.w("NeoForge installation not yet implemented")
        return Result.failure(Exception("NeoForge installation not yet implemented"))
    }

    private fun isQuiltInstalled(loaderVersion: String, minecraftVersion: String): Boolean {
        val versionId = "quilt-loader-$loaderVersion-$minecraftVersion"
        val versionDir = File(MCLauncherApp.versionsDirectory, versionId)
        return versionDir.exists() && File(versionDir, "$versionId.json").exists()
    }

    private fun isNeoForgeInstalled(loaderVersion: String, minecraftVersion: String): Boolean {
        val versionId = "$minecraftVersion-neoforge-$loaderVersion"
        val versionDir = File(MCLauncherApp.versionsDirectory, versionId)
        return versionDir.exists() && File(versionDir, "$versionId.json").exists()
    }

    private suspend fun getQuiltVersions(minecraftVersion: String): Result<List<String>> {
        return Result.success(emptyList())
    }

    private suspend fun getNeoForgeVersions(minecraftVersion: String): Result<List<String>> {
        return Result.success(emptyList())
    }
}
