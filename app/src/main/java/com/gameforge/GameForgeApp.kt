package com.gameforge

import android.app.Application
import com.gameforge.data.GameForgeDatabase
import com.gameforge.data.GameRepository

class GameForgeApp : Application() {
    val database by lazy { GameForgeDatabase.getInstance(this) }
    val repository by lazy { GameRepository(database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private var instance: GameForgeApp? = null

        fun getInstance(): GameForgeApp = instance
            ?: throw IllegalStateException("GameForgeApp not initialized")
    }
}