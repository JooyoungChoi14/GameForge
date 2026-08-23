package com.gameforge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════════════════
// Game DAO
// ═══════════════════════════════════════════════════════════════

@Dao
interface GameDao {

    @Query("SELECT * FROM games WHERE deletedAt IS NULL ORDER BY lastPlayedAt DESC")
    fun getAllActive(): Flow<List<GameEntry>>

    @Query("SELECT * FROM games WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getAllTrashed(): Flow<List<GameEntry>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GameEntry)

    @Update
    suspend fun update(game: GameEntry)

    @Query("UPDATE games SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE games SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM games WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeOldTrash(cutoff: Long)
}

// ═══════════════════════════════════════════════════════════════
// HTML Version DAO
// ═══════════════════════════════════════════════════════════════

@Dao
interface HtmlVersionDao {

    @Query("SELECT * FROM game_html_versions WHERE gameId = :gameId ORDER BY version ASC")
    fun getByGame(gameId: String): Flow<List<GameHtmlVersion>>

    @Query("SELECT * FROM game_html_versions WHERE gameId = :gameId ORDER BY version DESC LIMIT 1")
    suspend fun getLatest(gameId: String): GameHtmlVersion?

    @Query("SELECT * FROM game_html_versions WHERE id = :id")
    suspend fun getById(id: String): GameHtmlVersion?

    @Query("SELECT * FROM game_html_versions WHERE gameId = :gameId AND htmlSourceHash = :hash LIMIT 1")
    suspend fun getByHash(gameId: String, hash: String): GameHtmlVersion?

    @Query("SELECT MAX(version) FROM game_html_versions WHERE gameId = :gameId")
    suspend fun maxVersion(gameId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: GameHtmlVersion)
}

// ═══════════════════════════════════════════════════════════════
// State Snapshot DAO
// ═══════════════════════════════════════════════════════════════

@Dao
interface StateSnapshotDao {

    @Query("SELECT * FROM game_state_snapshots WHERE gameId = :gameId ORDER BY version DESC")
    fun getByGame(gameId: String): Flow<List<GameStateSnapshot>>

    @Query("SELECT * FROM game_state_snapshots WHERE gameId = :gameId AND source = 'auto' ORDER BY version DESC")
    suspend fun getAutoSnapshots(gameId: String): List<GameStateSnapshot>

    @Query("SELECT * FROM game_state_snapshots WHERE id = :id")
    suspend fun getById(id: String): GameStateSnapshot?

    @Query("SELECT MAX(version) FROM game_state_snapshots WHERE gameId = :gameId")
    suspend fun maxVersion(gameId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: GameStateSnapshot)

    @Query("DELETE FROM game_state_snapshots WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM game_state_snapshots WHERE gameId = :gameId AND source = 'auto'")
    suspend fun autoSnapshotCount(gameId: String): Int
}

// ═══════════════════════════════════════════════════════════════
// Chat Message DAO
// ═══════════════════════════════════════════════════════════════

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE gameId = :gameId ORDER BY timestamp ASC")
    fun getByGame(gameId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: String)
}

// ═══════════════════════════════════════════════════════════════
// LLM Provider DAO
// ═══════════════════════════════════════════════════════════════

@Dao
interface LlmProviderDao {

    @Query("SELECT * FROM llm_providers WHERE isEnabled = 1 ORDER BY sortOrder")
    fun getAllEnabled(): Flow<List<LlmProvider>>

    @Query("SELECT * FROM llm_providers WHERE id = :id")
    suspend fun getById(id: String): LlmProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: LlmProvider)

    @Update
    suspend fun update(provider: LlmProvider)
}