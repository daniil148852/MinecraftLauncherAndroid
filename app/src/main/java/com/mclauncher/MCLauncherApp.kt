package com.mclauncher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MCLauncherApp : Application(), ImageLoaderFactory {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
        const val GAME_CHANNEL_ID = "game_channel"
        
        lateinit var instance: MCLauncherApp
            private set

        val gameDirectory: File
            get() = File(instance.getExternalFilesDir(null), "minecraft")

        val versionsDirectory: File
            get() = File(gameDirectory, "versions")

        val librariesDirectory: File
            get() = File(gameDirectory, "libraries")

        val assetsDirectory: File
            get() = File(gameDirectory, "assets")

        val runtimeDirectory: File
            get() = File(instance.filesDir, "runtime")

        val modsDirectory: File
            get() = File(gameDirectory, "mods")

        val profilesDirectory: File
            get() = File(gameDirectory, "profiles")

        val nativesDirectory: File
            get() = File(instance.cacheDir, "natives")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        initTimber()
        createNotificationChannels()
        createDirectories()
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Download channel
            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(downloadChannel)

            // Game channel
            val gameChannel = NotificationChannel(
                GAME_CHANNEL_ID,
                getString(R.string.game_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.game_channel_description)
            }
            notificationManager.createNotificationChannel(gameChannel)
        }
    }

    private fun createDirectories() {
        listOf(
            gameDirectory,
            versionsDirectory,
            librariesDirectory,
            assetsDirectory,
            runtimeDirectory,
            modsDirectory,
            profilesDirectory,
            nativesDirectory
        ).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Timber.d("Created directory: ${dir.absolutePath}")
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // In release, only log errors and warnings
            if (priority >= android.util.Log.WARN) {
                // Could send to crashlytics or remote logging service
            }
        }
    }
}
