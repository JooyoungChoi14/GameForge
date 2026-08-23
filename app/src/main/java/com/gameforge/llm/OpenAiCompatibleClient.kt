package com.gameforge.llm

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI 호환 API 클라이언트.
 * Ollama, OpenAI, Anthropic (via proxy), Gemini 등 공통 인터페이스로 처리.
 * SSE 스트리밍 지원.
 */
class OpenAiCompatibleClient(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val model: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val gson = Gson()

    data class Message(
        val role: String,
        val content: String? = null,
        val tool_calls: List<ToolCallDto>? = null,
        val tool_call_id: String? = null,
    )

    data class ToolCallDto(
        val id: String,
        val type: String = "function",
        val function: FunctionCall,
    )

    data class FunctionCall(
        val name: String,
        val arguments: String,
    )

    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val stream: Boolean = false,
        val tools: List<ToolDef>? = null,
        val temperature: Double = 0.7,
        val max_tokens: Int = 4096,
    )

    data class ToolDef(
        val type: String = "function",
        val function: FunctionDef,
    )

    data class FunctionDef(
        val name: String,
        val description: String,
        val parameters: Parameters,
    )

    data class Parameters(
        val type: String = "object",
        val properties: Map<String, PropertyDef>,
        val required: List<String> = listOf(),
    )

    data class PropertyDef(
        val type: String,
        val description: String? = null,
    )

    /**
     * 비동기 채팅 완성 (스트리밍).
     * 각 토큰과 tool_call을 Flow로 방출.
     */
    fun chatCompletionStream(request: ChatRequest): Flow<StreamEvent> = flow {
        val jsonBody = gson.toJson(request.copy(stream = true))
        val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val reqBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/chat/completions")
            .post(body)

        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = withContext(Dispatchers.IO) {
            client.newCall(reqBuilder.build()).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(500) ?: "Unknown error"
            emit(StreamEvent.Error("HTTP ${response.code}: $errorBody"))
            return@flow
        }

        val reader = response.body?.byteStream()?.bufferedReader() ?: return@flow

        withContext(Dispatchers.IO) {
            try {
                var currentContent = StringBuilder()
                var currentToolCalls = mutableMapOf<Int, Pair<String, StringBuilder>>()
                var currentToolCallId = ""

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val choice = json.getAsJsonArray("choices")?.get(0)?.asJsonObject ?: continue
                        val delta = choice.getAsJsonObject("delta") ?: continue

                        // Content token
                        if (delta.has("content") && !delta.get("content").isJsonNull) {
                            val token = delta.get("content").asString
                            currentContent.append(token)
                            emit(StreamEvent.Token(token))
                        }

                        // Tool calls
                        if (delta.has("tool_calls")) {
                            val toolCallsArray = delta.getAsJsonArray("tool_calls")
                            toolCallsArray?.forEach { tcElement ->
                                val tc = tcElement.asJsonObject
                                val index = tc.get("index")?.asInt ?: 0
                                val func = tc.getAsJsonObject("function")

                                if (!currentToolCalls.containsKey(index)) {
                                    currentToolCalls[index] = Pair(
                                        func.get("name")?.asString ?: "",
                                        StringBuilder()
                                    )
                                }
                                func.get("arguments")?.asString?.let {
                                    currentToolCalls[index]!!.second.append(it)
                                }
                            }
                        }

                        // Finish reason
                        val finishReason = choice.get("finish_reason")?.asString
                        if (finishReason == "stop" || finishReason == "end_turn") {
                            emit(StreamEvent.Done(currentContent.toString()))
                        } else if (finishReason == "tool_calls") {
                            val calls = currentToolCalls.map { (_, pair) ->
                                LlmProvider.ToolCall(
                                    id = "call_${pair.first.hashCode().toString(16)}",
                                    name = pair.first,
                                    arguments = pair.second.toString(),
                                )
                            }
                            emit(StreamEvent.ToolCalls(calls))
                        }
                    } catch (e: Exception) {
                        // Skip malformed JSON chunks
                    }
                }
            } finally {
                reader.close()
            }
        }
    }

    /**
     * 비동기 채팅 완성 (비스트리밍).
     */
    suspend fun chatCompletion(request: ChatRequest): LlmProvider.ChatResult = withContext(Dispatchers.IO) {
        val jsonBody = gson.toJson(request.copy(stream = false))
        val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val reqBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/chat/completions")
            .post(body)

        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(reqBuilder.build()).execute()
        val responseBody = response.body?.string() ?: return@withContext LlmProvider.ChatResult(null, null, null)

        if (!response.isSuccessful) {
            return@withContext LlmProvider.ChatResult(null, null, null)
        }

        try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val choice = json.getAsJsonArray("choices")?.get(0)?.asJsonObject ?: return@withContext LlmProvider.ChatResult(null, null, null)
            val message = choice.getAsJsonObject("message") ?: return@withContext LlmProvider.ChatResult(null, null, null)

            val content = message.get("content")?.asString
            val toolCalls = message.getAsJsonArray("tool_calls")?.map { tcElement ->
                val tc = tcElement.asJsonObject
                val func = tc.getAsJsonObject("function")
                LlmProvider.ToolCall(
                    id = tc.get("id")?.asString ?: "",
                    name = func.get("name")?.asString ?: "",
                    arguments = func.get("arguments")?.asString ?: "{}",
                )
            }

            val usage = json.getAsJsonObject("usage")?.let { u ->
                LlmProvider.TokenUsage(
                    promptTokens = u.get("prompt_tokens")?.asInt ?: 0,
                    completionTokens = u.get("completion_tokens")?.asInt ?: 0,
                    totalTokens = u.get("total_tokens")?.asInt ?: 0,
                )
            }

            LlmProvider.ChatResult(content, toolCalls, usage)
        } catch (e: Exception) {
            LlmProvider.ChatResult(null, null, null)
        }
    }

    sealed class StreamEvent {
        data class Token(val text: String) : StreamEvent()
        data class Done(val fullContent: String) : StreamEvent()
        data class ToolCalls(val calls: List<LlmProvider.ToolCall>) : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}