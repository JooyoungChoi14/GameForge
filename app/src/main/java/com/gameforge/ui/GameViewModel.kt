package com.gameforge.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gameforge.GameForgeApp
import com.gameforge.data.*
import com.gameforge.engine.BrowserEngine
import com.gameforge.llm.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 게임 생명주기 + 상태 관리 ViewModel.
 * 대시보드, 게임 플레이, 채팅 제어, 버저닝을 모두 포괄.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "GameViewModel"
    private val repo = GameForgeApp.getInstance().repository
    private val stateManager = GameStateManager(repo)
    private val llmManager = LlmManager(application)
    private var agentLoop: GameAgentLoop? = null
    private var pipeline: GameGenerationPipeline? = null

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

    private val _isChatVisible = MutableStateFlow(false)
    val isChatVisible: StateFlow<Boolean> = _isChatVisible.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _generationState = MutableStateFlow<GameGenerationPipeline.GenerationState>(
        GameGenerationPipeline.GenerationState.Idle
    )
    val generationState: StateFlow<GameGenerationPipeline.GenerationState> = _generationState.asStateFlow()

    // ── 버저닝 상태 ────────────────────────────────────────────

    private val _htmlVersions = MutableStateFlow<List<GameHtmlVersion>>(emptyList())
    val htmlVersions: StateFlow<List<GameHtmlVersion>> = _htmlVersions.asStateFlow()

    private val _stateSnapshots = MutableStateFlow<List<GameStateSnapshot>>(emptyList())
    val stateSnapshots: StateFlow<List<GameStateSnapshot>> = _stateSnapshots.asStateFlow()

    // ── 프로바이더 ────────────────────────────────────────────────

    private val _activeProvider = MutableStateFlow<com.gameforge.data.LlmProvider?>(null)
    val activeProvider: StateFlow<com.gameforge.data.LlmProvider?> = _activeProvider.asStateFlow()

    val providers: StateFlow<List<com.gameforge.data.LlmProvider>> = repo.getAllEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LlmManager.DEFAULT_PROVIDERS)

    fun updateProvider(provider: com.gameforge.data.LlmProvider) {
        viewModelScope.launch {
            repo.updateProvider(provider)
            llmManager.invalidateClient(provider.id)
            if (_activeProvider.value?.id == provider.id) {
                _activeProvider.value = provider
            }
        }
    }

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

    fun selectGame(game: GameEntry, engine: BrowserEngine) {
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
        // 마지막 HTML 버전 로드
        viewModelScope.launch {
            val latestHtml = repo.getCurrentHtmlVersion(game.id)
            if (latestHtml != null) {
                engine.loadDataWithBaseURL(
                    baseUrl = "about:blank",
                    data = latestHtml.htmlSource,
                    mimeType = "text/html",
                    encoding = "UTF-8",
                    historyUrl = null,
                )
                // 마지막 상태 스냅샷 복원
                delay(500)
                val latestSnapshot = _stateSnapshots.value.firstOrNull()
                if (latestSnapshot != null) {
                    try {
                        engine.setGameState(latestSnapshot.gameStateJson)
                    } catch (e: Exception) {
                        Log.w(TAG, "State restore failed: ${e.message}")
                    }
                }
            }
        }
        startAutoSave()
    }

    fun deselectGame() {
        stopAutoSave()
        viewModelScope.launch { saveCurrentState(source = "manual", label = "종료 전 저장") }
        _currentGame.value = null
        _isChatVisible.value = false
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
        return stateManager.restoreFull(game.id, htmlVersionId, snapshotId)
    }

    // ── 게임 생성 ─────────────────────────────────────────────────

    fun generateNewGame(engine: BrowserEngine, genreHint: String? = null) {
        val provider = _activeProvider.value ?: return
        _isGenerating.value = true
        _generationState.value = GameGenerationPipeline.GenerationState.Generating("게임 생성 중…")

        viewModelScope.launch {
            val pipe = GameGenerationPipeline(llmManager, repo, stateManager)
            pipeline = pipe

            val existingNames = activeGames.value.map { it.name }

            val result = pipe.generateGame(
                provider = provider,
                engine = engine,
                existingGames = existingNames,
                genreHint = genreHint,
            )

            _generationState.value = result
            _isGenerating.value = false

            when (result) {
                is GameGenerationPipeline.GenerationState.Success -> {
                    // 생성된 게임 선택
                    repo.getGame(result.gameId)?.let { game ->
                        selectGame(game, engine)
                    }
                }
                is GameGenerationPipeline.GenerationState.Error -> {
                    _error.value = result.message
                }
                else -> {}
            }
        }
    }

    // ── 채팅 제어 ────────────────────────────────────────────────

    fun sendChatMessage(message: String, engine: BrowserEngine) {
        val game = _currentGame.value ?: return
        val provider = _activeProvider.value ?: return

        viewModelScope.launch {
            // 사용자 메시지 저장
            repo.addChatMessage(
                gameId = game.id,
                role = "user",
                content = message,
            )

            // Agent Loop 실행
            val loop = GameAgentLoop(llmManager, provider, repo, engine, game.id)
            val result = loop.processMessage(
                userMessage = message,
                chatHistory = _chatMessages.value,
            )

            // 어시스턴트 응답 저장
            repo.addChatMessage(
                gameId = game.id,
                role = "assistant",
                content = result.response,
            )

            // HTML 변경 시 버전 저장
            if (result.htmlChanged) {
                try {
                    val newHtml = engine.evalJs("document.documentElement.outerHTML")
                    if (newHtml.isNotBlank()) {
                        stateManager.snapshotHtml(
                            gameId = game.id,
                            htmlSource = newHtml,
                            source = "llm_edit",
                            description = "채팅 제어로 수정",
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "HTML snapshot after edit failed: ${e.message}")
                }
            }

            // 상태 변경 시 스냅샷
            if (result.stateChanged) {
                saveCurrentState(source = "llm", label = "채팅 제어 후")
            }
        }
    }

    // ── 프로바이더 설정 ──────────────────────────────────────────

    fun setActiveProvider(provider: com.gameforge.data.LlmProvider) {
        _activeProvider.value = provider
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

    fun clearError() {
        _error.value = null
    }

    fun toggleChat() {
        _isChatVisible.value = !_isChatVisible.value
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoSave()
    }
}