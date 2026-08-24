package com.gameforge

import android.app.Application
import android.util.Log
import com.gameforge.data.GameForgeDatabase
import com.gameforge.data.GameRepository
import com.gameforge.llm.LlmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GameForgeApp : Application() {
    val database by lazy { GameForgeDatabase.getInstance(this) }
    val repository by lazy { GameRepository(database) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        seedDefaultProviders()
    }

    private fun seedDefaultProviders() {
        appScope.launch {
            val dao = database.llmProviderDao()
            LlmManager.DEFAULT_PROVIDERS.forEach { provider ->
                val existing = dao.getById(provider.id)
                if (existing == null) {
                    dao.insert(provider)
                    Log.d(TAG, "Seeded provider: ${provider.id}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "GameForgeApp"

        @Volatile
        private var instance: GameForgeApp? = null

        fun getInstance(): GameForgeApp = instance
            ?: throw IllegalStateException("GameForgeApp not initialized")
    }
}