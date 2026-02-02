package com.mclauncher.di

import android.content.Context
import com.mclauncher.MCLauncherApp
import com.mclauncher.core.auth.AuthManager
import com.mclauncher.core.auth.MicrosoftAuthProvider
import com.mclauncher.core.auth.OfflineAuthProvider
import com.mclauncher.core.controls.ControlsManager
import com.mclauncher.core.download.DownloadManager
import com.mclauncher.core.launcher.GameLauncher
import com.mclauncher.core.modloader.FabricInstaller
import com.mclauncher.core.modloader.ForgeInstaller
import com.mclauncher.core.modloader.ModLoaderManager
import com.mclauncher.core.modloader.ModManager
import com.mclauncher.core.runtime.JREManager
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.repository.AccountRepository
import com.mclauncher.data.repository.ProfileRepository
import com.mclauncher.data.repository.VersionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplication(@ApplicationContext context: Context): MCLauncherApp {
        return context.applicationContext as MCLauncherApp
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        preferencesManager: PreferencesManager
    ): DownloadManager {
        return DownloadManager(context, ioDispatcher, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideOfflineAuthProvider(): OfflineAuthProvider {
        return OfflineAuthProvider()
    }

    @Provides
    @Singleton
    fun provideMicrosoftAuthProvider(
        @ApplicationContext context: Context
    ): MicrosoftAuthProvider {
        return MicrosoftAuthProvider(context)
    }

    @Provides
    @Singleton
    fun provideAuthManager(
        accountRepository: AccountRepository,
        offlineAuthProvider: OfflineAuthProvider,
        microsoftAuthProvider: MicrosoftAuthProvider,
        preferencesManager: PreferencesManager
    ): AuthManager {
        return AuthManager(
            accountRepository,
            offlineAuthProvider,
            microsoftAuthProvider,
            preferencesManager
        )
    }

    @Provides
    @Singleton
    fun provideJREManager(
        @ApplicationContext context: Context,
        downloadManager: DownloadManager,
        preferencesManager: PreferencesManager
    ): JREManager {
        return JREManager(context, downloadManager, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideFabricInstaller(
        @ApplicationContext context: Context,
        downloadManager: DownloadManager
    ): FabricInstaller {
        return FabricInstaller(context, downloadManager)
    }

    @Provides
    @Singleton
    fun provideForgeInstaller(
        @ApplicationContext context: Context,
        downloadManager: DownloadManager
    ): ForgeInstaller {
        return ForgeInstaller(context, downloadManager)
    }

    @Provides
    @Singleton
    fun provideModLoaderManager(
        fabricInstaller: FabricInstaller,
        forgeInstaller: ForgeInstaller
    ): ModLoaderManager {
        return ModLoaderManager(fabricInstaller, forgeInstaller)
    }

    @Provides
    @Singleton
    fun provideModManager(
        @ApplicationContext context: Context,
        profileRepository: ProfileRepository
    ): ModManager {
        return ModManager(context, profileRepository)
    }

    @Provides
    @Singleton
    fun provideControlsManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager
    ): ControlsManager {
        return ControlsManager(context, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideGameLauncher(
        @ApplicationContext context: Context,
        versionRepository: VersionRepository,
        profileRepository: ProfileRepository,
        accountRepository: AccountRepository,
        downloadManager: DownloadManager,
        jreManager: JREManager,
        modLoaderManager: ModLoaderManager,
        preferencesManager: PreferencesManager
    ): GameLauncher {
        return GameLauncher(
            context,
            versionRepository,
            profileRepository,
            accountRepository,
            downloadManager,
            jreManager,
            modLoaderManager,
            preferencesManager
        )
    }
}
