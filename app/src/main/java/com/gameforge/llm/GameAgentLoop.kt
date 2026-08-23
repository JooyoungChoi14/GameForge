package com.gameforge.llm

import com.gameforge.data.GameRepository
import com.gameforge.data.LlmProvider as LlmProviderEntity
import com.gameforge.engine.BrowserEngine
import com.gameforge.data.GameHtmlVersion
import com.gameforge.data.GameStateSnapshot

/**
 * 게임 제어 Agent Loop.
 * LLM → tool_call → evalJs → 결과 피드백 → LLM 사이클.
 * DevCompanion AgentLoop 패턴 재사용.
 */
class GameAgentLoop(
    private val llmManager: LlmManager,
    private val provider: LlmProviderEntity,
    private val repository: GameRepository,
    private val engine: BrowserEngine,
    private val gameId: String,
) {
    private val maxIterations = 8

    data class LoopResult(
        val response: String,
        val htmlChanged: Boolean = false,
        val stateChanged: Boolean = false,
        val iterations: Int = 0,
    )

    /**
     * 사용자 메시지를 처리하는 에이전트 루프.
     * LLM이 tool_call을 반환하면 실행 → 결과 피드백 → 최대 maxIterations까지 반복.
     */
    suspend fun processMessage(
        userMessage: String,
        chatHistory: List<com.gameforge.data.ChatMessage>,
    ): LoopResult {
        var iterations = 0
        var htmlChanged = false
        var stateChanged = false
        val responseBuilder = StringBuilder()
        var currentMessage = userMessage
        var history = chatHistory.map { LlmProvider.ChatMessage(
            role = it.role,
            content = it.content,
        ) }

        // 게임 현재 상태 읽기
        var gameStateJson = try {
            engine.getGameState()
        } catch (e: Exception) {
            "{}"
        }

        var gameInfoJson = try {
            engine.getGameInfo()
        } catch (e: Exception) {
            "{}"
        }

        val tools = buildToolDefinitions()

        while (iterations < maxIterations) {
            iterations++
            val result = llmManager.gameControlChat(
                provider = provider,
                gameName = extractName(gameInfoJson),
                gameInfo = gameInfoJson,
                gameState = gameStateJson,
                chatHistory = history,
                userMessage = currentMessage,
                tools = tools,
            )

            // 콘텐츠 응답 — 루프 종료
            if (!result.content.isNullOrBlank() && result.toolCalls.isNullOrEmpty()) {
                responseBuilder.append(result.content)
                break
            }

            // tool_call 처리
            if (!result.toolCalls.isNullOrEmpty()) {
                for (toolCall in result.toolCalls) {
                    val toolResult = executeToolCall(toolCall)
                    when (toolCall.name) {
                        "eval_js" -> {
                            // JS 실행 후 상태 변화 감지
                            stateChanged = true
                        }
                        "modify_game_code" -> {
                            htmlChanged = true
                        }
                    }
                    // tool 결과를 히스토리에 추가
                    history = history + LlmProvider.ChatMessage(
                        role = "assistant",
                        content = result.content ?: "",
                    )
                    history = history + LlmProvider.ChatMessage(
                        role = "tool",
                        content = toolResult,
                        toolCallId = toolCall.id,
                    )
                }

                // 상태 재읽기
                if (stateChanged || htmlChanged) {
                    gameStateJson = try { engine.getGameState() } catch (_: Exception) { gameStateJson }
                }

                currentMessage = "위 tool 실행 결과를 바탕으로 응답해주세요."
                continue
            }

            // 빈 응답 — 종료
            break
        }

        return LoopResult(
            response = responseBuilder.toString().ifBlank { "응답을 생성할 수 없습니다." },
            htmlChanged = htmlChanged,
            stateChanged = stateChanged,
            iterations = iterations,
        )
    }

    /** 게임 제어용 툴 정의 */
    private fun buildToolDefinitions(): List<OpenAiCompatibleClient.ToolDef> {
        return listOf(
            OpenAiCompatibleClient.ToolDef(
                function = OpenAiCompatibleClient.FunctionDef(
                    name = "eval_js",
                    description = "게임 WebView에서 JavaScript 코드를 실행합니다. 게임 상태를 읽거나 수정할 때 사용하세요.",
                    parameters = OpenAiCompatibleClient.Parameters(
                        properties = mapOf(
                            "js" to OpenAiCompatibleClient.PropertyDef(
                                type = "string",
                                description = "실행할 JavaScript 코드",
                            ),
                        ),
                        required = listOf("js"),
                    ),
                ),
            ),
            OpenAiCompatibleClient.ToolDef(
                function = OpenAiCompatibleClient.FunctionDef(
                    name = "read_state",
                    description = "현재 게임 상태를 JSON으로 읽습니다.",
                    parameters = OpenAiCompatibleClient.Parameters(
                        properties = emptyMap(),
                        required = emptyList(),
                    ),
                ),
            ),
            OpenAiCompatibleClient.ToolDef(
                function = OpenAiCompatibleClient.FunctionDef(
                    name = "modify_game_code",
                    description = "게임의 HTML 코드를 완전히 교체합니다. 난이도, 테마, 규칙 등을 근본적으로 변경할 때만 사용하세요.",
                    parameters = OpenAiCompatibleClient.Parameters(
                        properties = mapOf(
                            "html" to OpenAiCompatibleClient.PropertyDef(
                                type = "string",
                                description = "새로운 완전한 HTML 코드",
                            ),
                            "description" to OpenAiCompatibleClient.PropertyDef(
                                type = "string",
                                description = "변경 설명 (예: '네온 테마 적용', '속도 1.5배')",
                            ),
                        ),
                        required = listOf("html", "description"),
                    ),
                ),
            ),
            OpenAiCompatibleClient.ToolDef(
                function = OpenAiCompatibleClient.FunctionDef(
                    name = "set_difficulty",
                    description = "게임 난이도를 1-5 사이로 설정합니다.",
                    parameters = OpenAiCompatibleClient.Parameters(
                        properties = mapOf(
                            "level" to OpenAiCompatibleClient.PropertyDef(
                                type = "integer",
                                description = "난이도 레벨 (1-5)",
                            ),
                        ),
                        required = listOf("level"),
                    ),
                ),
            ),
            OpenAiCompatibleClient.ToolDef(
                function = OpenAiCompatibleClient.FunctionDef(
                    name = "create_snapshot",
                    description = "현재 게임 상태를 수동 저장합니다.",
                    parameters = OpenAiCompatibleClient.Parameters(
                        properties = mapOf(
                            "label" to OpenAiCompatibleClient.PropertyDef(
                                type = "string",
                                description = "저장 라벨 (예: 'Level 5 클리어')",
                            ),
                        ),
                        required = listOf("label"),
                    ),
                ),
            ),
        )
    }

    /** tool_call 실행 */
    private suspend fun executeToolCall(toolCall: LlmProvider.ToolCall): String {
        return when (toolCall.name) {
            "eval_js" -> {
                try {
                    val js = extractStringArg(toolCall.arguments, "js")
                    engine.evalJs(js)
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            "read_state" -> {
                try {
                    engine.getGameState()
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            "modify_game_code" -> {
                try {
                    val html = extractStringArg(toolCall.arguments, "html")
                    val description = extractStringArg(toolCall.arguments, "description")
                    // HTML 코드 교체
                    engine.loadDataWithBaseURL(
                        baseUrl = "about:blank",
                        data = html,
                        mimeType = "text/html",
                        encoding = "UTF-8",
                        historyUrl = null,
                    )
                    // 새 HTML 버전 저장
                    repository.saveHtmlVersion(
                        gameId = gameId,
                        htmlSource = html,
                        source = "llm_edit",
                        description = description,
                    )
                    "게임 코드가 변경되었습니다: $description"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            "set_difficulty" -> {
                try {
                    val level = extractIntArg(toolCall.arguments, "level")
                    engine.evalJsVoid("if(window.setDifficulty) window.setDifficulty($level)")
                    "난이도가 $level(으)로 변경되었습니다."
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            "create_snapshot" -> {
                try {
                    val label = extractStringArg(toolCall.arguments, "label")
                    val stateJson = engine.getGameState()
                    val htmlHash = repository.getCurrentHtmlHash(gameId) ?: ""
                    repository.saveStateSnapshot(
                        gameId = gameId,
                        gameStateJson = stateJson,
                        htmlSourceHash = htmlHash,
                        source = "llm",
                        label = label,
                    )
                    "스냅샷이 저장되었습니다: $label"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            else -> "Unknown tool: ${toolCall.name}"
        }
    }

    private fun extractStringArg(args: String, key: String): String {
        return try {
            val json = com.google.gson.JsonParser.parseString(args).asJsonObject
            json.get(key)?.asString ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractIntArg(args: String, key: String): Int {
        return try {
            val json = com.google.gson.JsonParser.parseString(args).asJsonObject
            json.get(key)?.asInt ?: 1
        } catch (_: Exception) {
            1
        }
    }

    private fun extractName(gameInfoJson: String): String {
        return try {
            val json = com.google.gson.JsonParser.parseString(gameInfoJson).asJsonObject
            json.get("name")?.asString ?: "게임"
        } catch (_: Exception) {
            "게임"
        }
    }
}