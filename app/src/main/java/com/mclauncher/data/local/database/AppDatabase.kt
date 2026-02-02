package com.mclauncher.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mclauncher.data.local.database.dao.AccountDao
import com.mclauncher.data.local.database.dao.ModDao
import com.mclauncher.data.local.database.dao.ProfileDao
import com.mclauncher.data.local.database.dao.VersionDao
import com.mclauncher.data.local.database.entities.AccountEntity
import com.mclauncher.data.local.database.entities.ModEntity
import com.mclauncher.data.local.database.entities.ProfileEntity
import com.mclauncher.data.local.database.entities.VersionEntity
import com.mclauncher.utils.Constants

@Database(
    entities = [
        ProfileEntity::class,
        VersionEntity::class,
        ModEntity::class,
        AccountEntity::class
    ],
    version = Constants.DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun versionDao(): VersionDao
    abstract fun modDao(): ModDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(*migrations)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val migrations = arrayOf<Migration>(
            // Future migrations will be added here
        )
    }

    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Initialize default data if needed
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(separator = "|||")
    }

    @androidx.room.TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split("|||")?.filter { it.isNotEmpty() }
    }

    @androidx.room.TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return value?.joinToString(separator = ",")
    }

    @androidx.room.TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return value?.split(",")?.mapNotNull { it.toLongOrNull() }
    }
}
