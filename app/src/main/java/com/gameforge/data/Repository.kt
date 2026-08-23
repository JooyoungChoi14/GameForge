package com.gameforge.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

// ═══════════════════════════════════════════════════════════════
// Repository — 단일 진실 공급원 (SSOT)
// ═══════════════════════════════════════════════════════════════

class GameRepository(private val db: GameForgeDatabase) {
    private val gameDao = db.gameDao()
    private val htmlDao = db.htmlVersionDao()
    private val snapshotDao = db.stateSnapshotDao()
    private val chatDao = db.chatMessageDao()

    // ── Game CRUD ──────────────────────────────────────────────

    fun getAllActive(): Flow<List<GameEntry>> = gameDao.getAllActive()
    fun getAllTrashed(): Flow<List<GameEntry>> = gameDao.getAllTrashed()

    suspend fun getGame(id: String): GameEntry? = gameDao.getById(id)

    suspend fun createGame(name: String, emoji: String, tags: List<String> = emptyList()): GameEntry {
        val game = GameEntry(
            id = UUID.randomUUID().toString(),
            name = name,
            emoji = emoji,
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = System.currentTimeMillis(),
            tags = tags.joinToString(","),
            htmlSourceHash = "", // initial placeholder, updated when first HTML version is saved
        )
        gameDao.insert(game)
        return game
    }

    suspend fun updateGame(game: GameEntry) = gameDao.update(game)

    suspend fun softDeleteGame(id: String) = gameDao.softDelete(id)
    suspend fun restoreGame(id: String) = gameDao.restore(id)
    suspend fun hardDeleteGame(id: String) = gameDao.hardDelete(id)

    // ── HTML Version ───────────────────────────────────────────

    fun getHtmlVersions(gameId: String): Flow<List<GameHtmlVersion>> = htmlDao.getByGame(gameId)

    suspend fun getCurrentHtmlVersion(gameId: String): GameHtmlVersion? = htmlDao.getLatest(gameId)

    suspend fun saveHtmlVersion(
        gameId: String,
        htmlSource: String,
        source: String,
        description: String? = null,
    ): GameHtmlVersion {
        val currentMax = htmlDao.maxVersion(gameId) ?: 0
        val version = GameHtmlVersion(
            id = UUID.randomUUID().toString(),
            gameId = gameId,
            version = currentMax + 1,
            createdAt = System.currentTimeMillis(),
            source = source,
            htmlSource = htmlSource,
            htmlSourceHash = sha256(htmlSource),
            description = description,
        )
        htmlDao.insert(version)

        // Update game's current HTML hash
        gameDao.getById(gameId)?.let { game ->
            gameDao.update(game.copy(htmlSourceHash = version.htmlSourceHash))
        }

        return version
    }

    suspend fun getHtmlVersionById(id: String): GameHtmlVersion? = htmlDao.getById(id)

    suspend fun getHtmlVersionByHash(gameId: String, hash: String): GameHtmlVersion? =
        htmlDao.getByHash(gameId, hash)

    suspend fun getCurrentHtmlHash(gameId: String): String? =
        htmlDao.getLatest(gameId)?.htmlSourceHash

    // ── State Snapshot ─────────────────────────────────────────

    fun getStateSnapshots(gameId: String): Flow<List<GameStateSnapshot>> = snapshotDao.getByGame(gameId)

    suspend fun saveStateSnapshot(
        gameId: String,
        gameStateJson: String,
        htmlSourceHash: String,
        source: String,
        label: String? = null,
        thumbnailPath: String? = null,
    ): GameStateSnapshot {
        val currentMax = snapshotDao.maxVersion(gameId) ?: 0
        val snapshot = GameStateSnapshot(
            id = UUID.randomUUID().toString(),
            gameId = gameId,
            version = currentMax + 1,
            createdAt = System.currentTimeMillis(),
            source = source,
            gameStateJson = gameStateJson,
            htmlSourceHash = htmlSourceHash,
            thumbnailPath = thumbnailPath,
            label = label,
        )
        snapshotDao.insert(snapshot)
        pruneAutoSnapshots(gameId)
        return snapshot
    }

    suspend fun getStateSnapshotById(id: String): GameStateSnapshot? = snapshotDao.getById(id)

    suspend fun nextStateVersion(gameId: String): Int = (snapshotDao.maxVersion(gameId) ?: 0) + 1
    suspend fun nextHtmlVersion(gameId: String): Int = (htmlDao.maxVersion(gameId) ?: 0) + 1

    private suspend fun pruneAutoSnapshots(gameId: String) {
        val autoSnapshots = snapshotDao.getAutoSnapshots(gameId)
        if (autoSnapshots.size > 20) {
            val toDelete = autoSnapshots.drop(20).map { it.id }
            snapshotDao.deleteByIds(toDelete)
        }
    }

    // ── Chat Messages ──────────────────────────────────────────

    fun getChatMessages(gameId: String): Flow<List<ChatMessage>> = chatDao.getByGame(gameId)

    suspend fun addChatMessage(
        gameId: String,
        role: String,
        content: String,
        toolCalls: String? = null,
        toolResult: String? = null,
    ): ChatMessage {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            gameId = gameId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            toolCalls = toolCalls,
            toolResult = toolResult,
        )
        chatDao.insert(message)
        return message
    }

    suspend fun clearChatMessages(gameId: String) = chatDao.deleteByGame(gameId)

    // ── Utility ────────────────────────────────────────────────

    companion object {
        fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}