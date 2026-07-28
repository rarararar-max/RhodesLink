package com.rhodes.privatechat.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String = "deepseek-v4-flash",
    val messages: List<AiMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.95,
    val max_tokens: Int? = null,
    val response_format: ResponseFormat? = null,
    val thinking: ThinkingParam? = null
)

@Serializable
data class ThinkingParam(val type: String)

@Serializable
data class ResponseFormat(
    val type: String
)

@Serializable
data class AiMessage(
    val role: String,
    val content: String,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

@Serializable
data class GroupTurnPlan(
    val user_intent: String = "",
    val goals: List<GroupTurnGoal> = emptyList()
)

@Serializable
data class GroupTurnGoal(
    val operator_id: String = "",
    val goal: String = ""
)

@Serializable
data class StreamChunk(
    val choices: List<StreamChoice>? = null
)

@Serializable
data class StreamChoice(
    val delta: Delta? = null
)

@Serializable
data class Delta(
    val content: String? = null
)

@Serializable
data class NonStreamResponse(
    val choices: List<NonStreamChoice>? = null,
    val usage: Usage? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int = 0,
    @SerialName("prompt_cache_miss_tokens") val promptCacheMissTokens: Int = 0
)

@Serializable
data class NonStreamChoice(
    val message: AiMessage? = null
)

@Serializable
data class StreamError(
    val error: StreamErrorDetail? = null
)

@Serializable
data class StreamErrorDetail(
    val message: String? = null
)

@Serializable
data class OfflineModeResponse(
    val emotion: String = "",
    val state: String = "",
    val location: String = "",
    val narration: String = "",
    val dialogue: String = "",
    val affection_mod: Int = 0,
    val segments: List<Segment>? = null
)

@Serializable
data class Segment(
    val type: String = "dialogue",
    val content: String = "",
    val speaker: String = ""
)

@Serializable
data class AnalysisResult(
    val intent_analysis: String = "",
    val user_emotion: String = "",
    val user_need: String = "",
    val suggested_emotion: String = "",
    val suggested_location: String = "",
    val suggested_state: String = "",
    val reply_guidance: String = "",
    val affection_mod: Int = 0
)

@Serializable
data class OnlineModeResponse(
    val emotion: String = "",
    val dialogue: String = "",
    val affection_mod: Int = 0
)

@Serializable
data class SummaryResponse(
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val anchors: List<AnchorItem> = emptyList()
)

@Serializable
data class AnchorItem(
    val type: String = "event",
    val content: String = "",
    val isPrivate: Boolean = false,
    val importance: String = "",
    val sourceActor: String = "",
    val sourceTarget: String = ""
)

@Serializable
data class UnifiedMemoryResponse(
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val anchors: List<AnchorItem> = emptyList(),
    val impression_update: ImpressionUpdate = ImpressionUpdate()
)

@Serializable
data class ImpressionUpdate(
    val should_update: Boolean = false,
    val impression: String = "",
    val keywords: List<String> = emptyList(),
    val preferences: List<String> = emptyList(),
    val taboos: List<String> = emptyList()
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val models: List<String>,
    val isOpenAICompat: Boolean = true
)

// === Google Gemini 请求/响应模型 ===

@Serializable
data class GoogleGenerationRequest(
    val contents: List<GoogleContent>,
    @SerialName("system_instruction") val systemInstruction: GoogleContent? = null,
    @SerialName("generationConfig") val generationConfig: GoogleGenerationConfig? = null
)

@Serializable
data class PrivateTurnAnalysis(
    val operator_emotion: String = "",
    val operator_location: String = "",
    val operator_activity: String = "",
    val user_intent: String = "",
    val reply_goal: String = ""
)

@Serializable
data class PrivateTurnState(
    val emotion: String = "平静",
    val location: String = "未确认",
    val activity: String = "未确认",
    val updatedAt: Long = 0L
)

@Serializable
data class GoogleGenerationConfig(
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null
)

@Serializable
data class GoogleContent(
    val parts: List<GooglePart>,
    val role: String = "user"
)

@Serializable
data class GooglePart(
    val text: String
)

@Serializable
data class GoogleGenerateResponse(
    val candidates: List<GoogleCandidate>? = null,
    @SerialName("usageMetadata") val usageMetadata: GoogleUsage? = null
)

@Serializable
data class GoogleCandidate(
    val content: GoogleContent? = null
)

@Serializable
data class GoogleUsage(
    @SerialName("promptTokenCount") val promptTokenCount: Int = 0,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int = 0,
    @SerialName("totalTokenCount") val totalTokenCount: Int = 0
)

@Serializable
data class DispatchSegment(
    val type: String = "",
    val content: String = "",
    val operator_states: List<DispatchOperatorState>? = null
)

@Serializable
data class DispatchOperatorState(
    val name: String = "",
    val emotion: String = ""
)

@Serializable
data class DispatchResponse(
    val segments: List<DispatchSegment>? = null,
    val items: List<String>? = null,
    val currency_reward: Int? = 0,
    val net_profit: Int? = 0
)

@Serializable
data class GroupMsgResult(
    val speaker: String = "",
    val message: String = "",
    val type: String = "dialogue"
)

@Serializable
data class DispatchEnd(
    val ending_content: String = "",
    val items: List<String> = emptyList(),
    val currency_reward: Int = 0,
    val net_profit: Int = 0
)

@Serializable
data class SuggestionResponse(
    val suggestions: List<String> = emptyList()
)

@Serializable
data class ImpressionResponse(
    val impression: String = "",
    val keywords: List<String> = emptyList(),
    val preferences: List<String> = emptyList(),
    val taboos: List<String> = emptyList()
)
