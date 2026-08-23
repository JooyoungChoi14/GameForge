package com.gameforge.llm

import android.util.Log
import com.gameforge.data.*
import com.gameforge.engine.BrowserEngine

/**
 * 게임 생성 파이프라인.
 * LLM 호출 → HTML 추출 → 보안 검증 → WebView 로드 → 메타데이터 추출 → DB 저장.
 */
class GameGenerationPipeline(
    private val llmManager: LlmManager,
    private val repository: GameRepository,
    private val stateManager: GameStateManager,
) {
    private val TAG = "GameGenPipeline"

    sealed class GenerationState {
        data object Idle : GenerationState()
        data class Generating(val progress: String) : GenerationState()
        data class Loading(val progress: String) : GenerationState()
        data class Validating(val progress: String) : GenerationState()
        data class Success(val gameId: String, val metadata: HtmlExtractor.GameMetadata?) : GenerationState()
        data class Error(val message: String, val retryable: Boolean) : GenerationState()
    }

    /**
     * 새 게임 생성 전체 파이프라인.
     * 최대 3회 재시도.
     */
    suspend fun generateGame(
        provider: com.gameforge.data.LlmProvider,
        engine: BrowserEngine,
        existingGames: List<String>,
        genreHint: String? = null,
        maxRetries: Int = 3,
    ): GenerationState {
        var lastError: String? = null

        repeat(maxRetries) { attempt ->
            try {
                // 1. LLM으로 게임 HTML 생성
                Log.d(TAG, "Generating game (attempt ${attempt + 1}/$maxRetries)")
                val response = llmManager.generateGame(provider, existingGames, genreHint)
                    ?: return GenerationState.Error("LLM 응답이 비어있습니다", true)

                // 2. HTML 추출
                val html = HtmlExtractor.extractHtml(response)
                    ?: return GenerationState.Error(
                        "게임 코드를 추출할 수 없습니다. 필수 JS 인터페이스(getGameState, setGameState, getGameInfo)가 포함되어야 합니다.",
                        attempt < maxRetries - 1
                    )

                // 3. 보안 검증
                val securityCheck = HtmlExtractor.validateSecurity(html)
                if (!securityCheck.isSecure) {
                    lastError = "보안 검증 실패: ${securityCheck.violations.joinToString(", ")}"
                    Log.w(TAG, lastError!!)
                    if (attempt < maxRetries - 1) {
                        // 재시도 시 보안 위반 내용을 프롬프트에 포함
                        return@repeat
                    }
                    return GenerationState.Error(lastError!!, false)
                }

                // 4. WebView에 로드
                Log.d(TAG, "Loading game HTML into WebView (${html.length} chars)")
                engine.loadDataWithBaseURL(
                    baseUrl = "about:blank",
                    data = html,
                    mimeType = "text/html",
                    encoding = "UTF-8",
                    historyUrl = null,
                )

                // 5. 게임 메타데이터 추출 (최대 3초 대기)
                var metadata: HtmlExtractor.GameMetadata? = null
                repeat(6) { // 500ms × 6 = 3초
                    try {
                        val infoJson = engine.getGameInfo()
                        if (infoJson.isNotBlank() && infoJson != "{}") {
                            metadata = HtmlExtractor.parseGameInfo(infoJson)
                            if (metadata != null) return@repeat
                        }
                    } catch (_: Exception) { /* 아직 로드 안 됨 */ }
                    Thread.sleep(500)
                }

                // 6. 게임 초기 상태 읽기
                var gameStateJson = "{}"
                try {
                    gameStateJson = engine.getGameState()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read initial game state: ${e.message}")
                }

                // 7. DB에 게임 저장
                val gameName = metadata?.name ?: "새 게임"
                val gameEmoji = metadata?.emoji ?: "🎮"
                val gameTags = metadata?.tags ?: emptyList()

                val game = repository.createGame(gameName, gameEmoji, gameTags)

                // 8. HTML 버전 저장
                stateManager.snapshotHtml(
                    gameId = game.id,
                    htmlSource = html,
                    source = "initial",
                    description = "초기 생성",
                )

                // 9. 초기 상태 스냅샷 저장
                val htmlHash = repository.getCurrentHtmlHash(game.id) ?: ""
                stateManager.snapshotState(
                    gameId = game.id,
                    gameStateJson = gameStateJson,
                    htmlSourceHash = htmlHash,
                    source = "manual",
                    label = "초기 상태",
                )

                Log.d(TAG, "Game generated successfully: ${game.id} ($gameName)")
                return GenerationState.Success(game.id, metadata)

            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                Log.e(TAG, "Game generation attempt ${attempt + 1} failed: $lastError")
            }
        }

        return GenerationState.Error(
            lastError ?: "게임 생성에 실패했습니다",
            false,
        )
    }
}