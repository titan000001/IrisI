package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CameraPresetEntity::class,
        UiLayoutEntity::class,
        CapturedMediaEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CineRigDatabase : RoomDatabase() {
    abstract fun cineRigDao(): CineRigDao

    companion object {
        @Volatile
        private var INSTANCE: CineRigDatabase? = null

        fun getInstance(context: Context): CineRigDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CineRigDatabase::class.java,
                    "cinerig_offline_vault.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
