package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorkSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PomidorDatabase : RoomDatabase() {
    abstract fun workSessionDao(): WorkSessionDao

    companion object {
        @Volatile
        private var INSTANCE: PomidorDatabase? = null

        fun getInstance(context: Context): PomidorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PomidorDatabase::class.java,
                    "pomidor_pro.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
