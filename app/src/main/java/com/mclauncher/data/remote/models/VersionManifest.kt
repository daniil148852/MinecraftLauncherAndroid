package com.mclauncher.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VersionManifest(
    @Json(name = "latest")
    val latest: LatestVersions,
    @Json(name = "versions")
    val versions: List<VersionInfo>
)

@JsonClass(generateAdapter = true)
data class LatestVersions(
    @Json(name = "release")
    val release: String,
    @Json(name = "snapshot")
    val snapshot: String
)

@JsonClass(generateAdapter = true)
data class VersionInfo(
    @Json(name = "id")
    val id: String,
    @Json(name = "type")
    val type: String,
    @Json(name = "url")
    val url: String,
    @Json(name = "time")
    val time: String,
    @Json(name = "releaseTime")
    val releaseTime: String,
    @Json(name = "sha1")
    val sha1: String,
    @Json(name = "complianceLevel")
    val complianceLevel: Int = 0
)

@JsonClass(generateAdapter = true)
data class VersionDetails(
    @Json(name = "id")
    val id: String,
    @Json(name = "type")
    val type: String,
    @Json(name = "time")
    val time: String,
    @Json(name = "releaseTime")
    val releaseTime: String,
    @Json(name = "mainClass")
    val mainClass: String,
    @Json(name = "minimumLauncherVersion")
    val minimumLauncherVersion: Int = 0,
    @Json(name = "arguments")
    val arguments: Arguments? = null,
    @Json(name = "minecraftArguments")
    val minecraftArguments: String? = null,
    @Json(name = "libraries")
    val libraries: List<Library>,
    @Json(name = "downloads")
    val downloads: Downloads,
    @Json(name = "assetIndex")
    val assetIndex: AssetIndexInfo,
    @Json(name = "assets")
    val assets: String,
    @Json(name = "javaVersion")
    val javaVersion: JavaVersion? = null,
    @Json(name = "logging")
    val logging: Logging? = null,
    @Json(name = "inheritsFrom")
    val inheritsFrom: String? = null
)

@JsonClass(generateAdapter = true)
data class Arguments(
    @Json(name = "game")
    val game: List<Any>? = null,
    @Json(name = "jvm")
    val jvm: List<Any>? = null
)

@JsonClass(generateAdapter = true)
data class Library(
    @Json(name = "name")
    val name: String,
    @Json(name = "downloads")
    val downloads: LibraryDownloads? = null,
    @Json(name = "rules")
    val rules: List<Rule>? = null,
    @Json(name = "natives")
    val natives: Map<String, String>? = null,
    @Json(name = "extract")
    val extract: ExtractRules? = null,
    @Json(name = "url")
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class LibraryDownloads(
    @Json(name = "artifact")
    val artifact: Artifact? = null,
    @Json(name = "classifiers")
    val classifiers: Map<String, Artifact>? = null
)

@JsonClass(generateAdapter = true)
data class Artifact(
    @Json(name = "path")
    val path: String,
    @Json(name = "sha1")
    val sha1: String,
    @Json(name = "size")
    val size: Long,
    @Json(name = "url")
    val url: String
)

@JsonClass(generateAdapter = true)
data class Rule(
    @Json(name = "action")
    val action: String,
    @Json(name = "os")
    val os: OsRule? = null,
    @Json(name = "features")
    val features: Map<String, Boolean>? = null
)

@JsonClass(generateAdapter = true)
data class OsRule(
    @Json(name = "name")
    val name: String? = null,
    @Json(name = "arch")
    val arch: String? = null,
    @Json(name = "version")
    val version: String? = null
)

@JsonClass(generateAdapter = true)
data class ExtractRules(
    @Json(name = "exclude")
    val exclude: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class Downloads(
    @Json(name = "client")
    val client: DownloadInfo,
    @Json(name = "client_mappings")
    val clientMappings: DownloadInfo? = null,
    @Json(name = "server")
    val server: DownloadInfo? = null,
    @Json(name = "server_mappings")
    val serverMappings: DownloadInfo? = null
)

@JsonClass(generateAdapter = true)
data class DownloadInfo(
    @Json(name = "sha1")
    val sha1: String,
    @Json(name = "size")
    val size: Long,
    @Json(name = "url")
    val url: String
)

@JsonClass(generateAdapter = true)
data class AssetIndexInfo(
    @Json(name = "id")
    val id: String,
    @Json(name = "sha1")
    val sha1: String,
    @Json(name = "size")
    val size: Long,
    @Json(name = "totalSize")
    val totalSize: Long,
    @Json(name = "url")
    val url: String
)

@JsonClass(generateAdapter = true)
data class JavaVersion(
    @Json(name = "component")
    val component: String,
    @Json(name = "majorVersion")
    val majorVersion: Int
)

@JsonClass(generateAdapter = true)
data class Logging(
    @Json(name = "client")
    val client: LoggingConfig? = null
)

@JsonClass(generateAdapter = true)
data class LoggingConfig(
    @Json(name = "argument")
    val argument: String,
    @Json(name = "file")
    val file: LoggingFile,
    @Json(name = "type")
    val type: String
)

@JsonClass(generateAdapter = true)
data class LoggingFile(
    @Json(name = "id")
    val id: String,
    @Json(name = "sha1")
    val sha1: String,
    @Json(name = "size")
    val size: Long,
    @Json(name = "url")
    val url: String
)

// Asset Index
@JsonClass(generateAdapter = true)
data class AssetIndex(
    @Json(name = "objects")
    val objects: Map<String, AssetObject>,
    @Json(name = "map_to_resources")
    val mapToResources: Boolean = false,
    @Json(name = "virtual")
    val virtual: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AssetObject(
    @Json(name = "hash")
    val hash: String,
    @Json(name = "size")
    val size: Long
)
