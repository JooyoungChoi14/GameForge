package com.gameforge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gameforge.GameForgeApp
import com.gameforge.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 게임 생명주기 + 상태 관리 ViewModel.
 * 대시보드, 게임 플레이, 채팅 제어를 모두 포괄.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = GameForgeApp.getInstance().repository
    private val stateManager = GameStateManager(repo)

    // ── 대시보드 상태 ───────────────────────────────────────────

    val activeGames: StateFlow<List<GameEntry>> = repo.getAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedGames: StateFlow<List<GameEntry>> = repo.getAllTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 현재 게임 상태 ──────────────────────────────────────────

    private val _currentGame = MutableStateFlow<GameEntry?>(null)
    val currentGame: StateFlow<GameEntry?> = _currentGame.asStateFlow()

    private val _gameState = MutableStateFlow<String?>(null)
    val gameState: StateFlow<String?> = _gameState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // ── 버저닝 상태 ────────────────────────────────────────────

    private val _htmlVersions = MutableStateFlow<List<GameHtmlVersion>>(emptyList())
    val htmlVersions: StateFlow<List<GameHtmlVersion>> = _htmlVersions.asStateFlow()

    private val _stateSnapshots = MutableStateFlow<List<GameStateSnapshot>>(emptyList())
    val stateSnapshots: StateFlow<List<GameStateSnapshot>> = _stateSnapshots.asStateFlow()

    // ── 자동 저장 ───────────────────────────────────────────────

    private var autoSaveJob: Job? = null

    fun startAutoSave(intervalMs: Long = 10_000L) {
        stopAutoSave()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(intervalMs)
                saveCurrentState(source = "auto")
            }
        }
    }

    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    // ── 게임 선택 ────────────────────────────────────────────────

    fun selectGame(game: GameEntry) {
        _currentGame.value = game
        viewModelScope.launch {
            repo.getHtmlVersions(game.id).collect { _htmlVersions.value = it }
        }
        viewModelScope.launch {
            repo.getStateSnapshots(game.id).collect { _stateSnapshots.value = it }
        }
        viewModelScope.launch {
            repo.getChatMessages(game.id).collect { _chatMessages.value = it }
        }
        startAutoSave()
    }

    fun deselectGame() {
        stopAutoSave()
        saveCurrentState(source = "manual", label = "종료 전 저장")
        _currentGame.value = null
    }

    // ── 상태 저장 ─────────────────────────────────────────────────

    suspend fun saveCurrentState(
        source: String = "manual",
        label: String? = null,
        thumbnailPath: String? = null,
    ) {
        val game = _currentGame.value ?: return
        val stateJson = _gameState.value ?: return
        val htmlHash = repo.getCurrentHtmlHash(game.id) ?: return

        stateManager.snapshotState(
            gameId = game.id,
            gameStateJson = stateJson,
            htmlSourceHash = htmlHash,
            source = source,
            label = label,
            thumbnailPath = thumbnailPath,
        )
    }

    // ── 롤백 ─────────────────────────────────────────────────────

    suspend fun restoreState(snapshotId: String): RestoreResult {
        val game = _currentGame.value ?: return RestoreResult.NotFound
        val result = stateManager.restoreState(game.id, snapshotId)
        if (result is RestoreResult.Success) {
            _gameState.value = result.stateJson
        }
        return result
    }

    suspend fun restoreFull(htmlVersionId: String, snapshotId: String?): RestoreResult {
        val game = _currentGame.value ?: return RestoreResult.NotFound
        val result = stateManager.restoreFull(game.id, htmlVersionId, snapshotId)
        if (result is RestoreResult.FullRestore) {
            // HTML and state will be applied by the UI layer via BrowserEngine
        }
        return result
    }

    // ── 게임 CRUD ─────────────────────────────────────────────────

    fun createGame(name: String, emoji: String, tags: List<String> = emptyList()) {
        viewModelScope.launch {
            val game = repo.createGame(name, emoji, tags)
            _currentGame.value = game
        }
    }

    fun deleteGame(id: String) {
        viewModelScope.launch { repo.softDeleteGame(id) }
    }

    fun restoreGame(id: String) {
        viewModelScope.launch { repo.restoreGame(id) }
    }

    fun permanentlyDeleteGame(id: String) {
        viewModelScope.launch { repo.hardDeleteGame(id) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoSave()
    }
}