package com.jeal.smartwidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppEntity::class, UsageEventEntity::class, TransitionEntity::class,
        ProfileEntity::class, ProfileAppEntity::class, WidgetEntity::class, MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apps(): AppDao
    abstract fun usage(): UsageDao
    abstract fun profiles(): ProfileDao
    abstract fun widgets(): WidgetDao
    abstract fun meta(): MetaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "smartwidget.db")
                .build().also { instance = it }
        }
    }
}
