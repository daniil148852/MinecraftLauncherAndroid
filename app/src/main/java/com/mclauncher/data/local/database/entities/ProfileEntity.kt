package com.mclauncher.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val versionId: String,
    val versionType: String,
    val modLoader: String = "none",
    val modLoaderVersion: String? = null,
    val gameDirectory: String? = null,
    val javaPath: String? = null,
    val jvmArguments: String? = null,
    val gameArguments: String? = null,
    val ramMb: Int = 2048,
    val width: Int = 1280,
    val height: Int = 720,
    val fullscreen: Boolean = false,
    val iconPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null,
    val playTime: Long = 0
)

@Entity(tableName = "versions")
data class VersionEntity(
    @PrimaryKey
    val id: String,
    val type: String,
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
)

@Entity(tableName = "mods")
data class ModEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val name: String,
    val fileName: String,
    val filePath: String,
    val version: String?,
    val description: String?,
    val authors: String?,
    val modLoader: String,
    val gameVersion: String?,
    val isEnabled: Boolean = true,
    val iconPath: String? = null,
    val downloadUrl: String? = null,
    val sha1: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val uuid: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accountType: String, // "offline", "microsoft"
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    val expiresAt: Long? = null
)
