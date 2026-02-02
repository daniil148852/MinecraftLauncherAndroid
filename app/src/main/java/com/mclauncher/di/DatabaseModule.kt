package com.mclauncher.di

import android.content.Context
import androidx.room.Room
import com.mclauncher.data.local.database.AppDatabase
import com.mclauncher.data.local.database.dao.AccountDao
import com.mclauncher.data.local.database.dao.ModDao
import com.mclauncher.data.local.database.dao.ProfileDao
import com.mclauncher.data.local.database.dao.VersionDao
import com.mclauncher.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideVersionDao(database: AppDatabase): VersionDao {
        return database.versionDao()
    }

    @Provides
    @Singleton
    fun provideModDao(database: AppDatabase): ModDao {
        return database.modDao()
    }

    @Provides
    @Singleton
    fun provideAccountDao(database: AppDatabase): AccountDao {
        return database.accountDao()
    }
}
