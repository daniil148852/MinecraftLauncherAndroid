package com.mclauncher.core.auth

import android.content.Context
import com.mclauncher.data.remote.api.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class MicrosoftAuthResult(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val skinUrl: String? = null,
    val capeUrl: String? = null
)

@Singleton
class MicrosoftAuthProvider @Inject constructor(
    private val context: Context
) {
    private val moshi = Moshi.Builder().build()
    private val client = OkHttpClient.Builder().build()

    companion object {
        // Azure AD application credentials
        // You need to register your app at https://portal.azure.com
        private const val CLIENT_ID = "YOUR_AZURE_CLIENT_ID" // Replace with your client ID
        private const val REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient"
        private const val SCOPE = "XboxLive.signin offline_access"

        // Microsoft OAuth endpoints
        private const val MICROSOFT_AUTH_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
        private const val MICROSOFT_TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

        // Xbox Live endpoints
        private const val XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
        private const val XBOX_XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"

        // Minecraft endpoints
        private const val MINECRAFT_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
    }

    fun getLoginUrl(): String {
        val params = mapOf(
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "scope" to SCOPE,
            "response_mode" to "query"
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

        return "$MICROSOFT_AUTH_URL?$queryString"
    }

    suspend fun authenticate(authCode: String): Result<MicrosoftAuthResult> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting Microsoft authentication...")

            // Step 1: Exchange auth code for Microsoft tokens
            val msTokens = exchangeCodeForTokens(authCode)
                ?: return@withContext Result.failure(Exception("Failed to get Microsoft tokens"))

            // Step 2: Authenticate with Xbox Live
            val xboxLiveToken = authenticateXboxLive(msTokens.accessToken)
                ?: return@withContext Result.failure(Exception("Failed to authenticate with Xbox Live"))

            // Step 3: Get XSTS token
            val xstsToken = getXSTSToken(xboxLiveToken.token)
                ?: return@withContext Result.failure(Exception("Failed to get XSTS token"))

            // Step 4: Authenticate with Minecraft
            val minecraftAuth = authenticateMinecraft(xstsToken.userHash, xstsToken.token)
                ?: return@withContext Result.failure(Exception("Failed to authenticate with Minecraft"))

            // Step 5: Get Minecraft profile
            val profile = getMinecraftProfile(minecraftAuth.accessToken)
                ?: return@withContext Result.failure(Exception("Failed to get Minecraft profile"))

            val result = MicrosoftAuthResult(
                username = profile.name,
                uuid = profile.id,
                accessToken = minecraftAuth.accessToken,
                refreshToken = msTokens.refreshToken,
                expiresAt = System.currentTimeMillis() + (minecraftAuth.expiresIn * 1000L),
                skinUrl = profile.skins?.firstOrNull { it.state == "ACTIVE" }?.url,
                capeUrl = profile.capes?.firstOrNull { it.state == "ACTIVE" }?.url
            )

            Timber.d("Microsoft authentication successful for ${profile.name}")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Microsoft authentication failed")
            Result.failure(e)
        }
    }

    suspend fun refreshToken(refreshToken: String): Result<MicrosoftAuthResult> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Refreshing Microsoft token...")

            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url(MICROSOFT_TOKEN_URL)
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(Exception("Token refresh failed: ${response.code}"))
            }

            val adapter = moshi.adapter(MicrosoftTokenResponse::class.java)
            val tokens = adapter.fromJson(body)
                ?: return@withContext Result.failure(Exception("Failed to parse token response"))

            // Re-authenticate with the new token
            val xboxLiveToken = authenticateXboxLive(tokens.accessToken)
                ?: return@withContext Result.failure(Exception("Failed to authenticate with Xbox Live"))

            val xstsToken = getXSTSToken(xboxLiveToken.token)
                ?: return@withContext Result.failure(Exception("Failed to get XSTS token"))

            val minecraftAuth = authenticateMinecraft(xstsToken.userHash, xstsToken.token)
                ?: return@withContext Result.failure(Exception("Failed to authenticate with Minecraft"))

            val profile = getMinecraftProfile(minecraftAuth.accessToken)
                ?: return@withContext Result.failure(Exception("Failed to get Minecraft profile"))

            val result = MicrosoftAuthResult(
                username = profile.name,
                uuid = profile.id,
                accessToken = minecraftAuth.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAt = System.currentTimeMillis() + (minecraftAuth.expiresIn * 1000L),
                skinUrl = profile.skins?.firstOrNull { it.state == "ACTIVE" }?.url,
                capeUrl = profile.capes?.firstOrNull { it.state == "ACTIVE" }?.url
            )

            Timber.d("Token refresh successful")
            Result.success(result)

        } catch (e: Exception) {
            Timber.e(e, "Token refresh failed")
            Result.failure(e)
        }
    }

    private suspend fun exchangeCodeForTokens(authCode: String): MicrosoftTokenResponse? {
        val formBody = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("code", authCode)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .add("scope", SCOPE)
            .build()

        val request = Request.Builder()
            .url(MICROSOFT_TOKEN_URL)
            .post(formBody)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Timber.e("Token exchange failed: $body")
            return null
        }

        val adapter = moshi.adapter(MicrosoftTokenResponse::class.java)
        return adapter.fromJson(body)
    }

    private suspend fun authenticateXboxLive(accessToken: String): XboxLiveAuthResponse? {
        val requestBody = XboxLiveAuthRequest(
            properties = XboxLiveProperties(
                authMethod = "RPS",
                siteName = "user.auth.xboxlive.com",
                rpsTicket = "d=$accessToken"
            ),
            relyingParty = "http://auth.xboxlive.com",
            tokenType = "JWT"
        )

        val adapter = moshi.adapter(XboxLiveAuthRequest::class.java)
        val json = adapter.toJson(requestBody)

        val request = Request.Builder()
            .url(XBOX_AUTH_URL)
            .post(okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"),
                json
            ))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Timber.e("Xbox Live auth failed: $body")
            return null
        }

        val responseAdapter = moshi.adapter(XboxLiveAuthResponse::class.java)
        return responseAdapter.fromJson(body)
    }

    private suspend fun getXSTSToken(xblToken: String): XSTSTokenResult? {
        val requestJson = """
            {
                "Properties": {
                    "SandboxId": "RETAIL",
                    "UserTokens": ["$xblToken"]
                },
                "RelyingParty": "rp://api.minecraftservices.com/",
                "TokenType": "JWT"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(XBOX_XSTS_URL)
            .post(okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"),
                requestJson
            ))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Timber.e("XSTS auth failed: $body")
            return null
        }

        val adapter = moshi.adapter(XboxLiveAuthResponse::class.java)
        val xstsResponse = adapter.fromJson(body) ?: return null

        return XSTSTokenResult(
            token = xstsResponse.token,
            userHash = xstsResponse.displayClaims.xui.firstOrNull()?.uhs ?: ""
        )
    }

    private suspend fun authenticateMinecraft(userHash: String, xstsToken: String): MinecraftAuthResponse? {
        val requestBody = MinecraftAuthRequest(
            identityToken = "XBL3.0 x=$userHash;$xstsToken"
        )

        val adapter = moshi.adapter(MinecraftAuthRequest::class.java)
        val json = adapter.toJson(requestBody)

        val request = Request.Builder()
            .url(MINECRAFT_AUTH_URL)
            .post(okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"),
                json
            ))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Timber.e("Minecraft auth failed: $body")
            return null
        }

        val responseAdapter = moshi.adapter(MinecraftAuthResponse::class.java)
        return responseAdapter.fromJson(body)
    }

    private suspend fun getMinecraftProfile(accessToken: String): MinecraftProfile? {
        val request = Request.Builder()
            .url(MINECRAFT_PROFILE_URL)
            .get()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Timber.e("Get profile failed: $body")
            return null
        }

        val adapter = moshi.adapter(MinecraftProfile::class.java)
        return adapter.fromJson(body)
    }
}

private data class XSTSTokenResult(
    val token: String,
    val userHash: String
)
