package com.mclauncher.data.remote.api

import com.mclauncher.data.remote.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface MojangApi {

    @GET("mc/game/version_manifest_v2.json")
    suspend fun getVersionManifest(): Response<VersionManifest>

    @GET
    suspend fun getVersionDetails(@Url url: String): Response<VersionDetails>

    @GET
    suspend fun getAssetIndex(@Url url: String): Response<AssetIndex>

    companion object {
        const val BASE_URL = "https://launchermeta.mojang.com/"
        const val RESOURCES_BASE_URL = "https://resources.download.minecraft.net/"
        const val LIBRARIES_BASE_URL = "https://libraries.minecraft.net/"
    }
}

interface MojangAuthApi {

    @GET("users/profiles/minecraft/{username}")
    suspend fun getUuidByUsername(@retrofit2.http.Path("username") username: String): Response<PlayerProfile>

    @GET("session/minecraft/profile/{uuid}")
    suspend fun getProfileByUuid(@retrofit2.http.Path("uuid") uuid: String): Response<PlayerProfile>

    companion object {
        const val BASE_URL = "https://api.mojang.com/"
        const val SESSION_BASE_URL = "https://sessionserver.mojang.com/"
    }
}

// Response models for Mojang Auth
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class PlayerProfile(
    @com.squareup.moshi.Json(name = "id")
    val id: String,
    @com.squareup.moshi.Json(name = "name")
    val name: String,
    @com.squareup.moshi.Json(name = "properties")
    val properties: List<ProfileProperty>? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ProfileProperty(
    @com.squareup.moshi.Json(name = "name")
    val name: String,
    @com.squareup.moshi.Json(name = "value")
    val value: String,
    @com.squareup.moshi.Json(name = "signature")
    val signature: String? = null
)

// Microsoft Auth models
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MicrosoftTokenResponse(
    @com.squareup.moshi.Json(name = "access_token")
    val accessToken: String,
    @com.squareup.moshi.Json(name = "token_type")
    val tokenType: String,
    @com.squareup.moshi.Json(name = "expires_in")
    val expiresIn: Int,
    @com.squareup.moshi.Json(name = "refresh_token")
    val refreshToken: String? = null,
    @com.squareup.moshi.Json(name = "scope")
    val scope: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class XboxLiveAuthRequest(
    @com.squareup.moshi.Json(name = "Properties")
    val properties: XboxLiveProperties,
    @com.squareup.moshi.Json(name = "RelyingParty")
    val relyingParty: String,
    @com.squareup.moshi.Json(name = "TokenType")
    val tokenType: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class XboxLiveProperties(
    @com.squareup.moshi.Json(name = "AuthMethod")
    val authMethod: String,
    @com.squareup.moshi.Json(name = "SiteName")
    val siteName: String,
    @com.squareup.moshi.Json(name = "RpsTicket")
    val rpsTicket: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class XboxLiveAuthResponse(
    @com.squareup.moshi.Json(name = "IssueInstant")
    val issueInstant: String,
    @com.squareup.moshi.Json(name = "NotAfter")
    val notAfter: String,
    @com.squareup.moshi.Json(name = "Token")
    val token: String,
    @com.squareup.moshi.Json(name = "DisplayClaims")
    val displayClaims: DisplayClaims
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class DisplayClaims(
    @com.squareup.moshi.Json(name = "xui")
    val xui: List<XuiClaim>
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class XuiClaim(
    @com.squareup.moshi.Json(name = "uhs")
    val uhs: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MinecraftAuthRequest(
    @com.squareup.moshi.Json(name = "identityToken")
    val identityToken: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MinecraftAuthResponse(
    @com.squareup.moshi.Json(name = "username")
    val username: String,
    @com.squareup.moshi.Json(name = "access_token")
    val accessToken: String,
    @com.squareup.moshi.Json(name = "token_type")
    val tokenType: String,
    @com.squareup.moshi.Json(name = "expires_in")
    val expiresIn: Int
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MinecraftProfile(
    @com.squareup.moshi.Json(name = "id")
    val id: String,
    @com.squareup.moshi.Json(name = "name")
    val name: String,
    @com.squareup.moshi.Json(name = "skins")
    val skins: List<Skin>? = null,
    @com.squareup.moshi.Json(name = "capes")
    val capes: List<Cape>? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class Skin(
    @com.squareup.moshi.Json(name = "id")
    val id: String,
    @com.squareup.moshi.Json(name = "state")
    val state: String,
    @com.squareup.moshi.Json(name = "url")
    val url: String,
    @com.squareup.moshi.Json(name = "variant")
    val variant: String? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class Cape(
    @com.squareup.moshi.Json(name = "id")
    val id: String,
    @com.squareup.moshi.Json(name = "state")
    val state: String,
    @com.squareup.moshi.Json(name = "url")
    val url: String,
    @com.squareup.moshi.Json(name = "alias")
    val alias: String? = null
)
