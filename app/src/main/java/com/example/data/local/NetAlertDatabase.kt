package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DisconnectLogEntity::class], version = 1, exportSchema = false)
abstract class NetAlertDatabase : RoomDatabase() {
    abstract fun disconnectLogDao(): DisconnectLogDao

    companion object {
        @Volatile
        private var INSTANCE: NetAlertDatabase? = null

        fun getDatabase(context: Context): NetAlertDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetAlertDatabase::class.java,
                    "netalert_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
