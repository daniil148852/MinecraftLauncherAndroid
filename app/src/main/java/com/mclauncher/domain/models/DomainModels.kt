package com.mclauncher.domain.models

import java.io.File
import java.util.UUID

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val uuid: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accountType: AccountType,
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    val expiresAt: Long? = null
) {
    val isExpired: Boolean
        get() = expiresAt != null && expiresAt < System.currentTimeMillis()

    val isOffline: Boolean
        get() = accountType == AccountType.OFFLINE
}

enum class AccountType {
    OFFLINE,
    MICROSOFT
}

data class GameVersion(
    val id: String,
    val type: VersionType,
    val url: String,
    val sha1: String,
    val releaseTime: String,
    val isInstalled: Boolean = false,
    val installPath: String? = null,
    val mainClass: String? = null,
    val assetsId: String? = null,
    val javaVersion: Int? = null,
    val totalSize: Long? = null,
    val downloadedSize: Long? = null
) {
    val downloadProgress: Float
        get() = if (totalSize != null && totalSize > 0 && downloadedSize != null) {
            downloadedSize.toFloat() / totalSize.toFloat()
        } else 0f

    val isDownloading: Boolean
        get() = downloadedSize != null && totalSize != null && downloadedSize < totalSize
}

enum class VersionType(val displayName: String) {
    RELEASE("Release"),
    SNAPSHOT("Snapshot"),
    OLD_BETA("Old Beta"),
    OLD_ALPHA("Old Alpha");

    companion object {
        fun fromString(type: String): VersionType {
            return when (type.lowercase()) {
                "release" -> RELEASE
                "snapshot" -> SNAPSHOT
                "old_beta" -> OLD_BETA
                "old_alpha" -> OLD_ALPHA
                else -> RELEASE
            }
        }
    }
}

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val versionId: String,
    val versionType: VersionType,
    val modLoader: ModLoader = ModLoader.NONE,
    val modLoaderVersion: String? = null,
    val gameDirectory: File? = null,
    val javaPath: String? = null,
    val jvmArguments: List<String> = emptyList(),
    val gameArguments: List<String> = emptyList(),
    val gameSettings: GameSettings = GameSettings(),
    val iconPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null,
    val playTime: Long = 0
) {
    val formattedPlayTime: String
        get() {
            val hours = playTime / 3600000
            val minutes = (playTime % 3600000) / 60000
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "< 1m"
            }
        }

    val displayVersion: String
        get() = if (modLoader != ModLoader.NONE && modLoaderVersion != null) {
            "$versionId (${modLoader.displayName} $modLoaderVersion)"
        } else {
            versionId
        }
}

enum class ModLoader(val displayName: String) {
    NONE("Vanilla"),
    FORGE("Forge"),
    FABRIC("Fabric"),
    QUILT("Quilt"),
    NEOFORGE("NeoForge");

    companion object {
        fun fromString(loader: String): ModLoader {
            return entries.find { it.name.equals(loader, ignoreCase = true) } ?: NONE
        }
    }
}

data class GameSettings(
    val ramMb: Int = 2048,
    val width: Int = 1280,
    val height: Int = 720,
    val fullscreen: Boolean = false,
    val renderer: String = "gl4es",
    val controlScale: Float = 1.0f,
    val controlOpacity: Float = 0.8f
)

data class Mod(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val name: String,
    val fileName: String,
    val filePath: String,
    val version: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val modLoader: ModLoader,
    val gameVersion: String? = null,
    val isEnabled: Boolean = true,
    val iconPath: String? = null,
    val downloadUrl: String? = null,
    val sha1: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    val formattedFileSize: String
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> "${fileSize / (1024 * 1024)} MB"
        }
}

data class LaunchConfig(
    val profile: Profile,
    val account: Account,
    val version: GameVersion,
    val javaPath: String,
    val gameDirectory: File,
    val nativesDirectory: File,
    val assetsDirectory: File,
    val librariesDirectory: File,
    val classpathEntries: List<File>,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val mainClass: String,
    val windowWidth: Int,
    val windowHeight: Int,
    val fullscreen: Boolean
)

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val destination: File,
    val sha1: String? = null,
    val size: Long = 0,
    val type: DownloadType,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val error: String? = null
)

enum class DownloadType {
    CLIENT_JAR,
    LIBRARY,
    ASSET,
    NATIVE,
    MOD_LOADER,
    JAVA_RUNTIME
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ControlButton(
    val id: String,
    val keyCode: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val opacity: Float = 1f,
    val isVisible: Boolean = true,
    val label: String? = null,
    val iconRes: Int? = null
)

data class ControlLayout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val buttons: List<ControlButton>,
    val isDefault: Boolean = false
)
