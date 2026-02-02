package com.mclauncher.di

import com.mclauncher.data.local.database.dao.AccountDao
import com.mclauncher.data.local.database.dao.ModDao
import com.mclauncher.data.local.database.dao.ProfileDao
import com.mclauncher.data.local.database.dao.VersionDao
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.data.remote.api.FabricApi
import com.mclauncher.data.remote.api.ForgeApi
import com.mclauncher.data.remote.api.MojangApi
import com.mclauncher.data.repository.AccountRepository
import com.mclauncher.data.repository.ModRepository
import com.mclauncher.data.repository.ProfileRepository
import com.mclauncher.data.repository.VersionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideVersionRepository(
        mojangApi: MojangApi,
        versionDao: VersionDao,
        preferencesManager: PreferencesManager
    ): VersionRepository {
        return VersionRepository(mojangApi, versionDao, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideProfileRepository(
        profileDao: ProfileDao,
        preferencesManager: PreferencesManager
    ): ProfileRepository {
        return ProfileRepository(profileDao, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideAccountRepository(
        accountDao: AccountDao,
        preferencesManager: PreferencesManager
    ): AccountRepository {
        return AccountRepository(accountDao, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideModRepository(
        modDao: ModDao,
        profileRepository: ProfileRepository,
        fabricApi: FabricApi,
        forgeApi: ForgeApi
    ): ModRepository {
        return ModRepository(modDao, profileRepository, fabricApi, forgeApi)
    }
}
