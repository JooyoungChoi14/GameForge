package com.gameforge.data

import androidx.room.*
import java.time.Instant

// ═══════════════════════════════════════════════════════════════
// Game Entry — 메인 게임 레코드
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "games")
data class GameEntry(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val createdAt: Long,          // epoch millis
    val lastPlayedAt: Long,
    val playDurationSeconds: Long = 0,
    val difficulty: Int = 1,       // 1-5
    val htmlSourceHash: String,    // current HTML version hash
    val tags: String = "",         // comma-separated: "puzzle,arcade"
    val deletedAt: Long? = null,   // soft delete (30-day trash)
)

// ═══════════════════════════════════════════════════════════════
// Game HTML Version — 코드 버저닝
// ═══════════════════════════════════════════════════════════════

@Entity(
    tableName = "game_html_versions",
    foreignKeys = [ForeignKey(
        entity = GameEntry::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId")]
)
data class GameHtmlVersion(
    @PrimaryKey val id: String,
    val gameId: String,
    val version: Int,
    val createdAt: Long,
    val source: String,             // "initial" | "llm_edit" | "manual"
    val htmlSource: String,
    val htmlSourceHash: String,     // SHA-256
    val description: String? = null,
)

// ═══════════════════════════════════════════════════════════════
// Game State Snapshot — 상태 버저닝
// ═══════════════════════════════════════════════════════════════

@Entity(
    tableName = "game_state_snapshots",
    foreignKeys = [ForeignKey(
        entity = GameEntry::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId"), Index("gameId", "htmlSourceHash")]
)
data class GameStateSnapshot(
    @PrimaryKey val id: String,
    val gameId: String,
    val version: Int,
    val createdAt: Long,
    val source: String,             // "auto" | "manual" | "llm" | "restore"
    val gameStateJson: String,
    val htmlSourceHash: String,     // compatible HTML version hash
    val thumbnailPath: String? = null,
    val label: String? = null,
)

// ═══════════════════════════════════════════════════════════════
// Chat Message — LLM 대화 기록
// ═══════════════════════════════════════════════════════════════

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = GameEntry::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId")]
)
data class ChatMessage(
    @PrimaryKey val id: String,
    val gameId: String,
    val role: String,               // "user" | "assistant" | "system" | "tool"
    val content: String,
    val timestamp: Long,
    val toolCalls: String? = null,  // JSON array of tool calls
    val toolResult: String? = null,  // tool result content
)

// ═══════════════════════════════════════════════════════════════
// LLM Provider Config
// ═══════════════════════════════════════════════════════════════

@Entity(tableName = "llm_providers")
data class LlmProvider(
    @PrimaryKey val id: String,     // "ollama" | "openai" | "anthropic" | "gemini"
    val name: String,
    val baseUrl: String,
    val apiKey: String? = null,
    val model: String,
    val providerType: String = "openai", // "openai" | "anthropic"
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
)