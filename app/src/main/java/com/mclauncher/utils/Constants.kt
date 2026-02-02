package com.mclauncher.utils

object Constants {
    // Mojang API URLs
    const val VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    const val RESOURCES_URL = "https://resources.download.minecraft.net/"
    const val LIBRARIES_URL = "https://libraries.minecraft.net/"

    // Fabric API URLs
    const val FABRIC_META_URL = "https://meta.fabricmc.net/"
    const val FABRIC_MAVEN_URL = "https://maven.fabricmc.net/"

    // Forge API URLs
    const val FORGE_MAVEN_URL = "https://maven.minecraftforge.net/"
    const val FORGE_PROMOTIONS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
    const val FORGE_VERSION_LIST_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.json"

    // JRE Download URLs (using AdoptOpenJDK/Temurin)
    const val JRE_DOWNLOAD_BASE = "https://api.adoptium.net/v3/binary/latest/"

    // Version Types
    const val VERSION_TYPE_RELEASE = "release"
    const val VERSION_TYPE_SNAPSHOT = "snapshot"
    const val VERSION_TYPE_OLD_BETA = "old_beta"
    const val VERSION_TYPE_OLD_ALPHA = "old_alpha"

    // Default Settings
    const val DEFAULT_RAM_MB = 2048
    const val MIN_RAM_MB = 512
    const val MAX_RAM_MB = 8192
    const val DEFAULT_WIDTH = 1280
    const val DEFAULT_HEIGHT = 720

    // Default JVM Arguments
    val DEFAULT_JVM_ARGS = listOf(
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=30",
        "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20",
        "-XX:G1HeapWastePercent=5",
        "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15",
        "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem",
        "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs",
        "-Daikars.new.flags=true"
    )

    // File Names
    const val LAUNCHER_PROFILES_FILE = "launcher_profiles.json"
    const val SERVERS_DAT_FILE = "servers.dat"
    const val OPTIONS_TXT_FILE = "options.txt"

    // Database
    const val DATABASE_NAME = "mclauncher_database"
    const val DATABASE_VERSION = 1

    // Preferences
    const val PREFERENCES_NAME = "mclauncher_preferences"
    const val PREF_SELECTED_PROFILE = "selected_profile"
    const val PREF_LAST_PLAYED_VERSION = "last_played_version"
    const val PREF_SHOW_SNAPSHOTS = "show_snapshots"
    const val PREF_SHOW_OLD_VERSIONS = "show_old_versions"
    const val PREF_AUTO_DOWNLOAD_JRE = "auto_download_jre"

    // Control Keycodes
    object KeyCodes {
        const val MOUSE_LEFT = -1
        const val MOUSE_RIGHT = -2
        const val MOUSE_MIDDLE = -3
        const val SCROLL_UP = -4
        const val SCROLL_DOWN = -5
        const val INVENTORY = 69 // E key
        const val DROP = 81 // Q key
        const val CHAT = 84 // T key
        const val COMMAND = 47 // / key
        const val SNEAK = 340 // Left Shift
        const val SPRINT = 341 // Left Ctrl
        const val PERSPECTIVE = 294 // F5
        const val SCREENSHOT = 291 // F2
        const val DEBUG = 293 // F3
        const val HOTBAR_1 = 49
        const val HOTBAR_2 = 50
        const val HOTBAR_3 = 51
        const val HOTBAR_4 = 52
        const val HOTBAR_5 = 53
        const val HOTBAR_6 = 54
        const val HOTBAR_7 = 55
        const val HOTBAR_8 = 56
        const val HOTBAR_9 = 57
    }

    // Mod Loaders
    object ModLoader {
        const val NONE = "none"
        const val FORGE = "forge"
        const val FABRIC = "fabric"
        const val QUILT = "quilt"
        const val NEOFORGE = "neoforge"
    }
}
