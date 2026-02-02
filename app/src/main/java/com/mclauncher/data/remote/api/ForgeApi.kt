package com.mclauncher.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface ForgeApi {

    @GET("net/minecraftforge/forge/maven-metadata.json")
    suspend fun getForgeVersions(): Response<Map<String, List<String>>>

    @GET("net/minecraftforge/forge/promotions_slim.json")
    suspend fun getForgePromotions(): Response<ForgePromotions>

    @GET
    suspend fun getForgeInstallerManifest(@Url url: String): Response<ForgeInstallerManifest>

    @GET
    suspend fun getForgeVersionJson(@Url url: String): Response<ForgeVersionJson>

    companion object {
        const val BASE_URL = "https://files.minecraftforge.net/"
        const val MAVEN_URL = "https://maven.minecraftforge.net/"

        fun getInstallerUrl(mcVersion: String, forgeVersion: String): String {
            val fullVersion = "$mcVersion-$forgeVersion"
            return "${MAVEN_URL}net/minecraftforge/forge/$fullVersion/forge-$fullVersion-installer.jar"
        }

        fun getUniversalUrl(mcVersion: String, forgeVersion: String): String {
            val fullVersion = "$mcVersion-$forgeVersion"
            return "${MAVEN_URL}net/minecraftforge/forge/$fullVersion/forge-$fullVersion-universal.jar"
        }
    }
}

@JsonClass(generateAdapter = true)
data class ForgePromotions(
    @Json(name = "homepage")
    val homepage: String,
    @Json(name = "promos")
    val promos: Map<String, String>
)

@JsonClass(generateAdapter = true)
data class ForgeInstallerManifest(
    @Json(name = "install")
    val install: ForgeInstallInfo,
    @Json(name = "versionInfo")
    val versionInfo: ForgeVersionInfo,
    @Json(name = "optionals")
    val optionals: List<ForgeOptional>? = null
)

@JsonClass(generateAdapter = true)
data class ForgeInstallInfo(
    @Json(name = "profileName")
    val profileName: String,
    @Json(name = "target")
    val target: String,
    @Json(name = "path")
    val path: String,
    @Json(name = "version")
    val version: String,
    @Json(name = "filePath")
    val filePath: String,
    @Json(name = "welcome")
    val welcome: String,
    @Json(name = "minecraft")
    val minecraft: String,
    @Json(name = "mirrorList")
    val mirrorList: String? = null,
    @Json(name = "logo")
    val logo: String? = null,
    @Json(name = "modList")
    val modList: String? = null
)

@JsonClass(generateAdapter = true)
data class ForgeVersionInfo(
    @Json(name = "id")
    val id: String,
    @Json(name = "time")
    val time: String,
    @Json(name = "releaseTime")
    val releaseTime: String,
    @Json(name = "type")
    val type: String,
    @Json(name = "minecraftArguments")
    val minecraftArguments: String? = null,
    @Json(name = "arguments")
    val arguments: ForgeArguments? = null,
    @Json(name = "mainClass")
    val mainClass: String,
    @Json(name = "inheritsFrom")
    val inheritsFrom: String? = null,
    @Json(name = "jar")
    val jar: String? = null,
    @Json(name = "libraries")
    val libraries: List<ForgeLibrary>
)

@JsonClass(generateAdapter = true)
data class ForgeArguments(
    @Json(name = "game")
    val game: List<Any>? = null,
    @Json(name = "jvm")
    val jvm: List<Any>? = null
)

@JsonClass(generateAdapter = true)
data class ForgeLibrary(
    @Json(name = "name")
    val name: String,
    @Json(name = "url")
    val url: String? = null,
    @Json(name = "downloads")
    val downloads: ForgeLibraryDownloads? = null,
    @Json(name = "checksums")
    val checksums: List<String>? = null,
    @Json(name = "serverreq")
    val serverReq: Boolean? = null,
    @Json(name = "clientreq")
    val clientReq: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ForgeLibraryDownloads(
    @Json(name = "artifact")
    val artifact: ForgeArtifact? = null
)

@JsonClass(generateAdapter = true)
data class ForgeArtifact(
    @Json(name = "path")
    val path: String,
    @Json(name = "url")
    val url: String,
    @Json(name = "sha1")
    val sha1: String,
    @Json(name = "size")
    val size: Long
)

@JsonClass(generateAdapter = true)
data class ForgeOptional(
    @Json(name = "name")
    val name: String,
    @Json(name = "client")
    val client: Boolean,
    @Json(name = "server")
    val server: Boolean,
    @Json(name = "default")
    val default: Boolean,
    @Json(name = "inject")
    val inject: Boolean,
    @Json(name = "desc")
    val desc: String,
    @Json(name = "url")
    val url: String,
    @Json(name = "artifact")
    val artifact: ForgeOptionalArtifact,
    @Json(name = "maven")
    val maven: String
)

@JsonClass(generateAdapter = true)
data class ForgeOptionalArtifact(
    @Json(name = "domain")
    val domain: String,
    @Json(name = "name")
    val name: String,
    @Json(name = "version")
    val version: String,
    @Json(name = "classifier")
    val classifier: String? = null,
    @Json(name = "ext")
    val ext: String? = null
)

// Modern Forge (1.13+) version json format
@JsonClass(generateAdapter = true)
data class ForgeVersionJson(
    @Json(name = "id")
    val id: String,
    @Json(name = "time")
    val time: String,
    @Json(name = "releaseTime")
    val releaseTime: String,
    @Json(name = "type")
    val type: String,
    @Json(name = "mainClass")
    val mainClass: String,
    @Json(name = "inheritsFrom")
    val inheritsFrom: String,
    @Json(name = "logging")
    val logging: Map<String, Any>? = null,
    @Json(name = "arguments")
    val arguments: ForgeArguments? = null,
    @Json(name = "libraries")
    val libraries: List<ForgeLibrary>
)

// NeoForge API models (fork of Forge for 1.20.1+)
interface NeoForgeApi {

    @GET("api/maven/versions/releases/net/neoforged/neoforge")
    suspend fun getNeoForgeVersions(): Response<NeoForgeVersionList>

    companion object {
        const val BASE_URL = "https://maven.neoforged.net/"
    }
}

@JsonClass(generateAdapter = true)
data class NeoForgeVersionList(
    @Json(name = "isSnapshot")
    val isSnapshot: Boolean,
    @Json(name = "versions")
    val versions: List<String>
)
