package com.gameforge.llm

/**
 * LLM 프로바이더 공통 인터페이스.
 * DevCompanion 4-프로바이더 패턴 재사용.
 */
interface LlmProvider {
    val id: String
    val name: String
    val baseUrl: String

    /** 비동기 스트리밍 채팅 완성 */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        model: String,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
    ): ChatResult

    data class ChatMessage(
        val role: String,    // "system" | "user" | "assistant" | "tool"
        val content: String,
        val toolCalls: List<ToolCall>? = null,
        val toolCallId: String? = null,
    )

    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: String,  // JSON string
    )

    data class ChatResult(
        val content: String?,
        val toolCalls: List<ToolCall>?,
        val usage: TokenUsage?,
    )

    data class TokenUsage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
    )
}