package com.mclauncher.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mclauncher_settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        // Account & Profile
        val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val LAST_PLAYED_VERSION = stringPreferencesKey("last_played_version")

        // Version Filters
        val SHOW_RELEASES = booleanPreferencesKey("show_releases")
        val SHOW_SNAPSHOTS = booleanPreferencesKey("show_snapshots")
        val SHOW_OLD_BETA = booleanPreferencesKey("show_old_beta")
        val SHOW_OLD_ALPHA = booleanPreferencesKey("show_old_alpha")

        // Game Settings
        val DEFAULT_RAM_MB = intPreferencesKey("default_ram_mb")
        val DEFAULT_WIDTH = intPreferencesKey("default_width")
        val DEFAULT_HEIGHT = intPreferencesKey("default_height")
        val DEFAULT_FULLSCREEN = booleanPreferencesKey("default_fullscreen")
        val CUSTOM_JVM_ARGS = stringPreferencesKey("custom_jvm_args")

        // Runtime Settings
        val AUTO_DOWNLOAD_JRE = booleanPreferencesKey("auto_download_jre")
        val JAVA_PATH = stringPreferencesKey("java_path")
        val USE_SYSTEM_JAVA = booleanPreferencesKey("use_system_java")

        // Download Settings
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val DOWNLOAD_VERIFY_CHECKSUMS = booleanPreferencesKey("download_verify_checksums")
        val AUTO_UPDATE_VERSIONS = booleanPreferencesKey("auto_update_versions")

        // UI Settings
        val DARK_MODE = stringPreferencesKey("dark_mode") // "system", "light", "dark"
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")

        // Controls Settings
        val CONTROL_SCALE = floatPreferencesKey("control_scale")
        val CONTROL_OPACITY = floatPreferencesKey("control_opacity")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val CUSTOM_CONTROLS_JSON = stringPreferencesKey("custom_controls_json")

        // Performance
        val RENDERER = stringPreferencesKey("renderer") // "gl4es", "virgl", "zink"
        val ALLOCATE_VRAM = intPreferencesKey("allocate_vram")

        // Misc
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val AGREED_TO_EULA = booleanPreferencesKey("agreed_to_eula")
        val SHOW_CONSOLE = booleanPreferencesKey("show_console")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppPreferences(
                activeAccountId = prefs[ACTIVE_ACCOUNT_ID],
                selectedProfileId = prefs[SELECTED_PROFILE_ID],
                lastPlayedVersion = prefs[LAST_PLAYED_VERSION],
                showReleases = prefs[SHOW_RELEASES] ?: true,
                showSnapshots = prefs[SHOW_SNAPSHOTS] ?: false,
                showOldBeta = prefs[SHOW_OLD_BETA] ?: false,
                showOldAlpha = prefs[SHOW_OLD_ALPHA] ?: false,
                defaultRamMb = prefs[DEFAULT_RAM_MB] ?: 2048,
                defaultWidth = prefs[DEFAULT_WIDTH] ?: 1280,
                defaultHeight = prefs[DEFAULT_HEIGHT] ?: 720,
                defaultFullscreen = prefs[DEFAULT_FULLSCREEN] ?: false,
                customJvmArgs = prefs[CUSTOM_JVM_ARGS],
                autoDownloadJre = prefs[AUTO_DOWNLOAD_JRE] ?: true,
                javaPath = prefs[JAVA_PATH],
                useSystemJava = prefs[USE_SYSTEM_JAVA] ?: false,
                maxConcurrentDownloads = prefs[MAX_CONCURRENT_DOWNLOADS] ?: 4,
                downloadVerifyChecksums = prefs[DOWNLOAD_VERIFY_CHECKSUMS] ?: true,
                autoUpdateVersions = prefs[AUTO_UPDATE_VERSIONS] ?: true,
                darkMode = prefs[DARK_MODE] ?: "system",
                dynamicColors = prefs[DYNAMIC_COLORS] ?: true,
                compactMode = prefs[COMPACT_MODE] ?: false,
                controlScale = prefs[CONTROL_SCALE] ?: 1.0f,
                controlOpacity = prefs[CONTROL_OPACITY] ?: 0.8f,
                vibrationEnabled = prefs[VIBRATION_ENABLED] ?: true,
                customControlsJson = prefs[CUSTOM_CONTROLS_JSON],
                renderer = prefs[RENDERER] ?: "gl4es",
                allocateVram = prefs[ALLOCATE_VRAM] ?: 512,
                firstLaunch = prefs[FIRST_LAUNCH] ?: true,
                agreedToEula = prefs[AGREED_TO_EULA] ?: false,
                showConsole = prefs[SHOW_CONSOLE] ?: false,
                keepScreenOn = prefs[KEEP_SCREEN_ON] ?: true
            )
        }

    suspend fun <T> updatePreference(key: Preferences.Key<T>, value: T) {
        dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    suspend fun setActiveAccount(accountId: String?) {
        dataStore.edit { prefs ->
            if (accountId != null) {
                prefs[ACTIVE_ACCOUNT_ID] = accountId
            } else {
                prefs.remove(ACTIVE_ACCOUNT_ID)
            }
        }
    }

    suspend fun setSelectedProfile(profileId: String?) {
        dataStore.edit { prefs ->
            if (profileId != null) {
                prefs[SELECTED_PROFILE_ID] = profileId
            } else {
                prefs.remove(SELECTED_PROFILE_ID)
            }
        }
    }

    suspend fun setVersionFilters(
        showReleases: Boolean,
        showSnapshots: Boolean,
        showOldBeta: Boolean,
        showOldAlpha: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[SHOW_RELEASES] = showReleases
            prefs[SHOW_SNAPSHOTS] = showSnapshots
            prefs[SHOW_OLD_BETA] = showOldBeta
            prefs[SHOW_OLD_ALPHA] = showOldAlpha
        }
    }

    suspend fun setDefaultGameSettings(
        ramMb: Int,
        width: Int,
        height: Int,
        fullscreen: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_RAM_MB] = ramMb
            prefs[DEFAULT_WIDTH] = width
            prefs[DEFAULT_HEIGHT] = height
            prefs[DEFAULT_FULLSCREEN] = fullscreen
        }
    }

    suspend fun setControlSettings(
        scale: Float,
        opacity: Float,
        vibration: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[CONTROL_SCALE] = scale
            prefs[CONTROL_OPACITY] = opacity
            prefs[VIBRATION_ENABLED] = vibration
        }
    }

    suspend fun markFirstLaunchComplete() {
        dataStore.edit { prefs ->
            prefs[FIRST_LAUNCH] = false
        }
    }

    suspend fun clearAllPreferences() {
        dataStore.edit { it.clear() }
    }
}

data class AppPreferences(
    val activeAccountId: String? = null,
    val selectedProfileId: String? = null,
    val lastPlayedVersion: String? = null,
    val showReleases: Boolean = true,
    val showSnapshots: Boolean = false,
    val showOldBeta: Boolean = false,
    val showOldAlpha: Boolean = false,
    val defaultRamMb: Int = 2048,
    val defaultWidth: Int = 1280,
    val defaultHeight: Int = 720,
    val defaultFullscreen: Boolean = false,
    val customJvmArgs: String? = null,
    val autoDownloadJre: Boolean = true,
    val javaPath: String? = null,
    val useSystemJava: Boolean = false,
    val maxConcurrentDownloads: Int = 4,
    val downloadVerifyChecksums: Boolean = true,
    val autoUpdateVersions: Boolean = true,
    val darkMode: String = "system",
    val dynamicColors: Boolean = true,
    val compactMode: Boolean = false,
    val controlScale: Float = 1.0f,
    val controlOpacity: Float = 0.8f,
    val vibrationEnabled: Boolean = true,
    val customControlsJson: String? = null,
    val renderer: String = "gl4es",
    val allocateVram: Int = 512,
    val firstLaunch: Boolean = true,
    val agreedToEula: Boolean = false,
    val showConsole: Boolean = false,
    val keepScreenOn: Boolean = true
)
