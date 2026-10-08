package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.decodeBase64Bytes
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 火山引擎（豆包语音）V3 单向流式语音合成。
 *
 * 与其它网关的差别：
 * - 鉴权用控制台创建的 `X-Api-Key`（一个值），不是火山 OpenAPI 的 AK/SK，也不需要 AppID/Token。
 * - 版本靠请求头 `X-Api-Resource-Id` 选择（`seed-icl-2.0` / `seed-icl-1.0`），**音色 ID 必须与版本匹配**，
 *   否则会返回 `resource ID is mismatched with speaker related resource`。
 * - 响应是 NDJSON：**每一行一个 JSON 帧**，音频 base64 放在 `data.audio`；不能把整个 body 当音频文件。
 * - 单次文本上限 1024 字节（中文约 300 字），超了报 `40402003`，所以这里按句子切分后分段合成再拼接。
 */
class VolcanoTtsGateway(
    private val endpoint: String,
    private val apiKey: String,
    /** X-Api-Resource-Id：seed-icl-2.0（复刻 2.0）或 seed-icl-1.0（复刻 1.0）。 */
    private val resourceId: String,
) : TtsGateway {
    private val client = createHttpClient()

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        require(request.voiceId.isNotBlank()) {
            "火山引擎需要在角色编辑页填写音色 ID（控制台「音色库 - 我的音色」里的 Speaker ID，S_ 开头）"
        }
        val speechText = prepareTtsSpeech(request.text, MAX_TOTAL_CHARS, "我在。")
        val segments = splitVolcanoTtsText(speechText)
        if (segments.isEmpty()) error("火山 TTS 没有可合成的文本")
        // 我们的语速是倍率（1.0 正常），火山是 [-50,100] 的百分点：1.5 倍 → 50，0.5 倍 → -50。
        val speechRate = ((request.speed.coerceIn(0.1, 2.0) - 1.0) * 100).roundToInt().coerceIn(-50, 100)
        val audio = ArrayList<ByteArray>(segments.size)
        var billedChars = 0
        segments.forEach { segment ->
            val result = synthesizeSegment(segment, request.voiceId, speechRate, request.format)
            if (result.first.isNotEmpty()) audio += result.first
            billedChars += result.second
        }
        val bytes = concat(audio)
        if (bytes.isEmpty()) error("火山 TTS 返回空音频")
        // 时长按计费字符数估算（火山按字符计费），仅用于界面进度显示。
        return TtsResult(audioBytes = bytes, durationMs = (billedChars.coerceAtLeast(1) * 180L).coerceAtMost(120_000L))
    }

    private suspend fun synthesizeSegment(text: String, speaker: String, speechRate: Int, format: String): Pair<ByteArray, Int> {
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("X-Api-Key", apiKey)
            header("X-Api-Resource-Id", resourceId)
            header("X-Api-Request-Id", randomRequestId())
            // 让结束帧带上本次计费字符数，便于调试面板展示消耗。
            header("X-Control-Require-Usage-Tokens-Return", "*")
            setBody(VolcanoRequest(req_params = VolcanoReqParams(
                text = text,
                speaker = speaker,
                // 复刻 2.0 建议显式传 model；1.0 下该参数不生效，传了会报 InvalidModel。
                model = modelForResource(resourceId),
                audio_params = VolcanoAudioParams(
                    format = format.ifBlank { "mp3" }.let { if (it == "wav") "wav" else it },
                    speech_rate = speechRate,
                ),
                additions = """{"disable_markdown_filter":true}""",
            )))
        }
        if (!response.status.isSuccess()) {
            val raw = runCatching { response.bodyAsText() }.getOrDefault("")
            error(volcanoTtsErrorMessage(rawCode(raw), rawMessage(raw).ifBlank { raw.take(300) }))
        }
        val channel = response.bodyAsChannel()
        val audio = ArrayList<ByteArray>()
        var billed = 0
        while (true) {
            val line = runCatching { channel.readUTF8Line() }.getOrNull() ?: break
            if (line.isBlank()) {
                if (channel.isClosedForRead) break else continue
            }
            val frame = parseVolcanoTtsFrame(line)
            if (frame.errorCode != null) {
                error(volcanoTtsErrorMessage(frame.errorCode, frame.message))
            }
            frame.usageChars?.let { billed = it }
            frame.audioBase64?.let { encoded ->
                if (encoded.isNotBlank()) {
                    runCatching { audio += encoded.decodeBase64Bytes() }
                        .onFailure { error("火山 TTS 音频帧无法解码：${it.javaClass.simpleName}") }
                }
            }
            if (frame.isLast) break
            if (channel.isClosedForRead) break
        }
        return concat(audio) to billed
    }

    private fun concat(chunks: List<ByteArray>): ByteArray {
        if (chunks.isEmpty()) return ByteArray(0)
        if (chunks.size == 1) return chunks.first()
        val output = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(output, offset)
            offset += chunk.size
        }
        return output
    }

    private fun rawCode(raw: String): Int? = runCatching {
        json.parseToJsonElement(raw).jsonObject["code"]?.jsonPrimitive?.content?.toIntOrNull()
    }.getOrNull()

    private fun rawMessage(raw: String): String = runCatching {
        json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.content.orEmpty()
    }.getOrDefault("")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val MAX_TOTAL_CHARS = 800
    }
}

@Serializable private data class VolcanoRequest(val req_params: VolcanoReqParams)

@Serializable private data class VolcanoReqParams(
    val text: String,
    val speaker: String,
    val model: String? = null,
    val audio_params: VolcanoAudioParams,
    val additions: String? = null,
)

@Serializable private data class VolcanoAudioParams(
    val format: String = "mp3",
    val sample_rate: Int = 24_000,
    val bit_rate: Int = 64_000,
    val speech_rate: Int = 0,
    val loudness_rate: Int = 0,
)

/** 解析出的一帧。`audioBase64` 是本帧音频，`isLast` 表示可以停止读取。 */
data class VolcanoTtsFrame(
    val audioBase64: String? = null,
    val isLast: Boolean = false,
    val usageChars: Int? = null,
    val errorCode: Int? = null,
    val message: String = "",
)

/**
 * 解析一行 NDJSON 帧。
 *
 * 按官方说明：`code=0` 是音频数据帧，`code=20000000`（旧文档写作 3000）是结束帧并可能带 `usage`，
 * 其它 code 一律当错误；`is_last=true` 也必须立刻停止读取（不要等连接断开）。
 */
fun parseVolcanoTtsFrame(line: String): VolcanoTtsFrame {
    val trimmed = line.trim().removePrefix("data:").trim()
    if (trimmed.isBlank()) return VolcanoTtsFrame()
    val root = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        ?: return VolcanoTtsFrame()
    val code = root["code"]?.jsonPrimitive?.content?.toIntOrNull()
    val message = root["message"]?.jsonPrimitive?.content.orEmpty()
    val data = root["data"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val audio = data?.get("audio")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    val usage = root["usage"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val usageChars = usage?.get("text_words")?.jsonPrimitive?.content?.toIntOrNull()
    val isLast = root["is_last"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true ||
        code == 20_000_000 || code == 3_000
    val isError = code != null && code != 0 && code != 20_000_000 && code != 3_000
    return VolcanoTtsFrame(
        audioBase64 = audio,
        isLast = isLast,
        usageChars = usageChars,
        errorCode = if (isError) code else null,
        message = message,
    )
}

/** 复刻 2.0 需要显式指定 model；1.0 下该参数不生效（传了会报 InvalidModel），因此返回 null。 */
fun modelForResource(resourceId: String): String? =
    if (resourceId.contains("icl-2.0")) "seed-tts-2.0-standard" else null

/**
 * 按句子把文本切成多段：单次上限 1024 字节（中文约 300 字，超限报 40402003）。
 * 这里用更保守的 280 字 / 1000 字节，优先在句末切分，避免把一句话拦腰截断。
 */
fun splitVolcanoTtsText(text: String, maxChars: Int = 280, maxBytes: Int = 1_000): List<String> {
    val normalized = text.trim()
    if (normalized.isEmpty()) return emptyList()
    if (normalized.length <= maxChars && normalized.encodeToByteArray().size <= maxBytes) return listOf(normalized)
    val sentences = mutableListOf<String>()
    val builder = StringBuilder()
    normalized.forEach { ch ->
        builder.append(ch)
        if (ch in SENTENCE_ENDINGS) {
            sentences += builder.toString()
            builder.clear()
        }
    }
    if (builder.isNotEmpty()) sentences += builder.toString()

    val out = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) {
            out += current.toString().trim()
            current.clear()
        }
    }
    sentences.forEach { sentence ->
        var rest = sentence
        while (rest.isNotEmpty()) {
            val roomChars = maxChars - current.length
            val roomBytes = maxBytes - current.toString().encodeToByteArray().size
            val candidate = if (roomChars <= 0 || roomBytes <= 0) "" else rest.take(roomChars)
            val fits = candidate.isNotEmpty() && candidate.encodeToByteArray().size <= roomBytes
            if (fits) {
                current.append(candidate)
                rest = rest.drop(candidate.length)
                if (current.toString().encodeToByteArray().size >= maxBytes) flush()
            } else {
                // 当前累积放不下：先落盘，放不下的这句单独成段并按上限硬切（极长的无标点文本）。
                if (current.isEmpty()) {
                    val hardLimit = minOf(maxChars, rest.length)
                    val piece = rest.take(hardLimit)
                    out += piece.trim()
                    rest = rest.drop(piece.length)
                } else {
                    flush()
                }
            }
        }
    }
    flush()
    return out.filter { it.isNotBlank() }
}

private val SENTENCE_ENDINGS = setOf('。', '！', '？', '!', '?', '…', '；', ';', '\n')

/** 每次请求一个新的 UUID，便于用 X-Tt-Logid 排障。 */
private fun randomRequestId(): String {
    val bytes = Random.nextBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = bytes.joinToString("") { byte -> ((byte.toInt() and 0xff) or 0x100).toString(16).substring(1) }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

/**
 * 把火山返回的错误码翻译成玩家能看懂、并且知道下一步做什么的中文提示。
 * 对照表来自官方错误码文档；未知错误保留原始 code 与 message，便于客服排障。
 */
fun volcanoTtsErrorMessage(code: Int?, message: String): String {
    val raw = message.trim()
    val lower = raw.lowercase()
    val hint = when {
        code == null -> "火山 TTS 返回无法解析：${raw.take(200)}"
        code == 40_200_011 || code == 40_200_012 || lower.contains("qps") || lower.contains("concurrency") ->
            "火山 TTS 触发限流（并发或 QPS 超限），请稍后再试：$code $raw"
        lower.contains("authentication") || lower.contains("no token") || lower.contains("access_key") || lower.contains("grant not found") ->
            "火山 TTS 鉴权失败：请在模型设置里重新填写控制台创建的 API Key（$code）"
        lower.contains("access denied") || lower.contains("quota exceeded") ->
            "火山 TTS 无资源权限或额度已用尽：请确认已开通「声音复刻」服务并购买对应资源包（$code）"
        lower.contains("mismatched") ->
            "音色与模型版本不匹配：2.0 复刻的音色要选 seed-icl-2.0，1.0 的要选 seed-icl-1.0（$code）"
        code == 45_002_000 -> "音色 ID 为空：请在角色编辑页填写火山音色 ID（$code）"
        code == 45_000_001 && lower.contains("speaker") -> "音色不存在：请核对角色编辑页里的 Speaker ID 是否与控制台一致（$code）"
        code == 45_001_115 || code == 45_001_107 -> "音色已释放或不存在：后付费音色 7 天未合成会被释放，请到控制台重新训练（$code）"
        code == 40_402_003 || lower.contains("textlimit") -> "文本超过单次上限（约 300 字）：请缩短内容（$code）"
        code == 45_002_001 -> "这段内容没有可朗读的文本（$code）"
        lower.contains("timeout") -> "火山 TTS 请求超时，请稍后重试（$code）"
        code == 55_000_000 || code == 55_001_309 -> "火山 TTS 服务端错误，请稍后重试（$code $raw）"
        else -> "火山 TTS 错误 $code：${raw.take(200)}"
    }
    return hint
}
