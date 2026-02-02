package com.mclauncher.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface FabricApi {

    @GET("v2/versions/game")
    suspend fun getGameVersions(): Response<List<FabricGameVersion>>

    @GET("v2/versions/loader")
    suspend fun getLoaderVersions(): Response<List<FabricLoaderVersion>>

    @GET("v2/versions/installer")
    suspend fun getInstallerVersions(): Response<List<FabricInstallerVersion>>

    @GET("v2/versions/loader/{gameVersion}")
    suspend fun getLoadersForGameVersion(
        @Path("gameVersion") gameVersion: String
    ): Response<List<FabricLoaderInfo>>

    @GET("v2/versions/loader/{gameVersion}/{loaderVersion}")
    suspend fun getLoaderInfo(
        @Path("gameVersion") gameVersion: String,
        @Path("loaderVersion") loaderVersion: String
    ): Response<FabricLoaderInfo>

    @GET("v2/versions/loader/{gameVersion}/{loaderVersion}/profile/json")
    suspend fun getVersionJson(
        @Path("gameVersion") gameVersion: String,
        @Path("loaderVersion") loaderVersion: String
    ): Response<FabricVersionJson>

    companion object {
        const val BASE_URL = "https://meta.fabricmc.net/"
        const val MAVEN_URL = "https://maven.fabricmc.net/"
    }
}

@JsonClass(generateAdapter = true)
data class FabricGameVersion(
    @Json(name = "version")
    val version: String,
    @Json(name = "stable")
    val stable: Boolean
)

@JsonClass(generateAdapter = true)
data class FabricLoaderVersion(
    @Json(name = "separator")
    val separator: String,
    @Json(name = "build")
    val build: Int,
    @Json(name = "maven")
    val maven: String,
    @Json(name = "version")
    val version: String,
    @Json(name = "stable")
    val stable: Boolean
)

@JsonClass(generateAdapter = true)
data class FabricInstallerVersion(
    @Json(name = "url")
    val url: String,
    @Json(name = "maven")
    val maven: String,
    @Json(name = "version")
    val version: String,
    @Json(name = "stable")
    val stable: Boolean
)

@JsonClass(generateAdapter = true)
data class FabricLoaderInfo(
    @Json(name = "loader")
    val loader: FabricLoaderVersion,
    @Json(name = "intermediary")
    val intermediary: FabricIntermediary,
    @Json(name = "launcherMeta")
    val launcherMeta: FabricLauncherMeta
)

@JsonClass(generateAdapter = true)
data class FabricIntermediary(
    @Json(name = "maven")
    val maven: String,
    @Json(name = "version")
    val version: String,
    @Json(name = "stable")
    val stable: Boolean
)

@JsonClass(generateAdapter = true)
data class FabricLauncherMeta(
    @Json(name = "version")
    val version: Int,
    @Json(name = "libraries")
    val libraries: FabricLibraries,
    @Json(name = "mainClass")
    val mainClass: FabricMainClass
)

@JsonClass(generateAdapter = true)
data class FabricLibraries(
    @Json(name = "client")
    val client: List<FabricLibrary>,
    @Json(name = "common")
    val common: List<FabricLibrary>,
    @Json(name = "server")
    val server: List<FabricLibrary>? = null
)

@JsonClass(generateAdapter = true)
data class FabricLibrary(
    @Json(name = "name")
    val name: String,
    @Json(name = "url")
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class FabricMainClass(
    @Json(name = "client")
    val client: String,
    @Json(name = "server")
    val server: String? = null
)

@JsonClass(generateAdapter = true)
data class FabricVersionJson(
    @Json(name = "id")
    val id: String,
    @Json(name = "inheritsFrom")
    val inheritsFrom: String,
    @Json(name = "releaseTime")
    val releaseTime: String,
    @Json(name = "time")
    val time: String,
    @Json(name = "type")
    val type: String,
    @Json(name = "mainClass")
    val mainClass: String,
    @Json(name = "arguments")
    val arguments: FabricArguments? = null,
    @Json(name = "libraries")
    val libraries: List<FabricLibraryDownload>
)

@JsonClass(generateAdapter = true)
data class FabricArguments(
    @Json(name = "game")
    val game: List<String>? = null,
    @Json(name = "jvm")
    val jvm: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class FabricLibraryDownload(
    @Json(name = "name")
    val name: String,
    @Json(name = "url")
    val url: String? = null
)
