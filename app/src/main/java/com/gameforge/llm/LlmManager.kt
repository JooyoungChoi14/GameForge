package com.gameforge.llm

import android.content.Context
import com.gameforge.data.LlmProvider
import kotlinx.coroutines.flow.Flow

/**
 * LLM 호출 통합 관리자.
 * 4-프로바이더 지원: Ollama, OpenAI, Anthropic, Gemini
 * DevCompanion LlmRepository 패턴 재사용.
 */
class LlmManager(private val context: Context) {

    private val clients = mutableMapOf<String, OpenAiCompatibleClient>()

    /** 프로바이더 클라이언트 획득 (캐시) */
    fun getClient(provider: LlmProvider): OpenAiCompatibleClient {
        return clients.getOrPut(provider.id) {
            OpenAiCompatibleClient(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                model = provider.model,
            )
        }
    }

    /** 게임 생성 요청 */
    suspend fun generateGame(
        provider: LlmProvider,
        existingGames: List<String>,
        genreHint: String? = null,
    ): String? {
        val client = getClient(provider)
        val prompt = GamePromptBuilder.buildGenerationPrompt(existingGames, genreHint)

        val request = OpenAiCompatibleClient.ChatRequest(
            model = provider.model,
            messages = listOf(
                OpenAiCompatibleClient.Message(
                    role = "system",
                    content = GamePromptBuilder.GAME_GENERATION_SYSTEM,
                ),
                OpenAiCompatibleClient.Message(
                    role = "user",
                    content = prompt,
                ),
            ),
            stream = false,
            temperature = 0.85,
            max_tokens = 8192,
        )

        val result = client.chatCompletion(request)
        return result.content
    }

    /** 게임 생성 스트리밍 */
    fun generateGameStream(
        provider: LlmProvider,
        existingGames: List<String>,
        genreHint: String? = null,
    ): Flow<OpenAiCompatibleClient.StreamEvent> {
        val client = getClient(provider)
        val prompt = GamePromptBuilder.buildGenerationPrompt(existingGames, genreHint)

        val request = OpenAiCompatibleClient.ChatRequest(
            model = provider.model,
            messages = listOf(
                OpenAiCompatibleClient.Message(
                    role = "system",
                    content = GamePromptBuilder.GAME_GENERATION_SYSTEM,
                ),
                OpenAiCompatibleClient.Message(
                    role = "user",
                    content = prompt,
                ),
            ),
            stream = true,
            temperature = 0.85,
            max_tokens = 8192,
        )

        return client.chatCompletionStream(request)
    }

    /** 게임 제어 채팅 (Agent Loop) */
    suspend fun gameControlChat(
        provider: LlmProvider,
        gameName: String,
        gameInfo: String,
        gameState: String,
        chatHistory: List<LlmProvider.ChatMessage>,
        userMessage: String,
        tools: List<OpenAiCompatibleClient.ToolDef>,
    ): LlmProvider.ChatResult {
        val client = getClient(provider)
        val systemPrompt = GamePromptBuilder.gameControlSystem(gameName, gameInfo, gameState)

        val messages = mutableListOf<OpenAiCompatibleClient.Message>()
        messages.add(OpenAiCompatibleClient.Message(role = "system", content = systemPrompt))

        // 채팅 히스토리 변환
        chatHistory.forEach { msg ->
            messages.add(OpenAiCompatibleClient.Message(
                role = msg.role,
                content = msg.content,
            ))
        }

        // 사용자 메시지 추가
        messages.add(OpenAiCompatibleClient.Message(role = "user", content = userMessage))

        val request = OpenAiCompatibleClient.ChatRequest(
            model = provider.model,
            messages = messages,
            tools = if (tools.isNotEmpty()) tools else null,
            stream = false,
            temperature = 0.5,
            max_tokens = 2048,
        )

        return client.chatCompletion(request)
    }

    companion object {
        // 기본 프로바이더 설정
        val DEFAULT_PROVIDERS = listOf(
            LlmProvider(
                id = "ollama",
                name = "Ollama (Cloud)",
                baseUrl = "https://ollama.com",
                apiKey = null,
                model = "glm-5.1",
                isEnabled = true,
                sortOrder = 0,
            ),
            LlmProvider(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com",
                apiKey = null, // 사용자 입력 필요
                model = "gpt-4o",
                isEnabled = false,
                sortOrder = 1,
            ),
            LlmProvider(
                id = "anthropic",
                name = "Anthropic",
                baseUrl = "https://api.anthropic.com",
                apiKey = null,
                model = "claude-sonnet-4-20250514",
                isEnabled = false,
                sortOrder = 2,
            ),
            LlmProvider(
                id = "gemini",
                name = "Gemini",
                baseUrl = "https://generativelanguage.googleapis.com",
                apiKey = null,
                model = "gemini-2.5-flash",
                isEnabled = false,
                sortOrder = 3,
            ),
        )
    }
}