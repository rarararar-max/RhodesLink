package com.rhodes.privatechat.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String = "deepseek-chat",
    val messages: List<AiMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.95
)

@Serializable
data class AiMessage(
    val role: String,
    val content: String
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
    @SerialName("total_tokens") val totalTokens: Int = 0
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
    val isPrivate: Boolean = false
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val models: List<String>,
    val isOpenAICompat: Boolean = true
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
