package com.focuscoin.app

import android.app.Application
import androidx.room.Room
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context

private val Context.focusCoinDataStore by preferencesDataStore(name = "focuscoin_settings")

class FocusCoinApplication : Application() {
    lateinit var repository: FocusCoinRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, FocusCoinDatabase::class.java, "focuscoin.db")
            .fallbackToDestructiveMigration()
            .build()
        repository = FocusCoinRepository(database, focusCoinDataStore)
    }
}
