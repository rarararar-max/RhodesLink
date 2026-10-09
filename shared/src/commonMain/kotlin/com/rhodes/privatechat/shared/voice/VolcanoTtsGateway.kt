package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.decodeBase64Bytes
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 火山引擎（豆包语音）V3 单向流式语音合成。
 *
 * 与其它网关的差别：
 * - 鉴权用控制台创建的 `X-Api-Key`（一个值），不是火山 OpenAPI 的 AK/SK，也不需要 AppID/Token。
 * - 版本靠请求头 `X-Api-Resource-Id` 选择（`seed-icl-2.0` / `seed-icl-1.0`），**音色 ID 必须与版本匹配**，
 *   否则会返回 `resource ID is mismatched with speaker related resource`。
 * - 响应是 NDJSON：**每一行一个 JSON 帧**，音频是**顶层 `data` 字段里的一串 base64**（旧版才是 `data.audio` /
 *   `payload.audio`），必须逐帧解码后按顺序拼接；不能把整个 body 当音频文件。
 * - 结束帧是 `code=20000000`（旧文档写 3000）或 `is_last=true`，可能带 `usage.text_words` 计费字符数。
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
        val bytes = concatAudio(audio)
        if (bytes.isEmpty()) error("火山 TTS 返回空音频")
        // 时长按计费字符数估算（火山按字符计费），仅用于界面进度显示。
        return TtsResult(audioBytes = bytes, durationMs = (billedChars.coerceAtLeast(1) * 180L).coerceAtMost(120_000L))
    }

    private suspend fun synthesizeSegment(text: String, speaker: String, speechRate: Int, format: String): Pair<ByteArray, Int> {
        val requestId = randomRequestId()
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("X-Api-Key", apiKey)
            header("X-Api-Resource-Id", resourceId)
            header("X-Api-Request-Id", requestId)
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
            // 火山 V3 的错误体是 {"header":{"code":..,"message":..,"request_id":..}}，只有旧版才是顶层 code/message。
            // 以前只读顶层字段，真实原因（音色不匹配 / 未开通 / 鉴权失败）会被吞掉，只剩一句“返回无法解析”，
            // 所以这里两种信封都读，并把 HTTP 状态、资源版本、音色、请求 ID、服务端 LogID 一起带出去给玩家和客服。
            val envelope = parseVolcanoResponseEnvelope(raw)
            val detail = volcanoDiagnosticDetail(
                httpStatus = response.status.value,
                resourceId = resourceId,
                speaker = speaker,
                clientRequestId = requestId,
                serverLogId = responseLogId(response),
                serverRequestId = envelope?.requestId.orEmpty(),
            )
            error(volcanoTtsErrorMessage(envelope?.code, envelope?.message?.takeIf { it.isNotBlank() } ?: raw, detail))
        }
        val serverLogId = responseLogId(response)
        val collector = VolcanoTtsFrameCollector()
        // 曾经这里是 `runCatching { channel.readUTF8Line() }.getOrNull() ?: break`：
        // 读行 API 有字符数上限、而异常又被静默当成“流结束”，几十 KB 的音频帧一帧都收不到，
        // 最终只剩一句“返回空音频”。现在改成按字节分帧 + 异常原样抛出，见 readNdjsonFrames。
        readNdjsonFrames(response.bodyAsChannel()) { line ->
            collector.accept(line) { serverRequestId ->
                volcanoDiagnosticDetail(
                    httpStatus = response.status.value,
                    resourceId = resourceId,
                    speaker = speaker,
                    clientRequestId = requestId,
                    serverLogId = serverLogId,
                    serverRequestId = serverRequestId,
                    streamed = true,
                )
            }
        }
        val bytes = collector.audioBytes()
        if (bytes.isEmpty()) {
            error(
                buildString {
                    append("火山 TTS 返回空音频（读取到 ")
                    append(collector.frameCount)
                    append(" 帧，")
                    append(if (collector.sawEndFrame) "已收到结束帧" else "未收到结束帧")
                    append("）：没有一帧带音频数据")
                    append("（")
                    append(
                        volcanoDiagnosticDetail(
                            httpStatus = response.status.value,
                            resourceId = resourceId,
                            speaker = speaker,
                            clientRequestId = requestId,
                            serverLogId = serverLogId,
                            streamed = true,
                        )
                    )
                    append("）")
                }
            )
        }
        return bytes to (collector.lastUsageChars ?: 0)
    }

    /**
     * 逐块读取 NDJSON 响应体，按 `\n` 分帧后交给 [onFrame]；[onFrame] 返回 true 表示可以停止（收到结束帧）。
     *
     * 不用 Ktor 的 `readUTF8Line()`：
     * - 它是按“字符数”计上限的读行 API，一旦被传了 max，实测单帧 3~100 KB 的音频帧就会抛 TooLongLineException；
     * - 更隐蔽的是异常会被 `runCatching{}.getOrNull() ?: break` 吞成“流结束”，导致一帧音频都拿不到。
     * 这里按字节分帧（跨 chunk 的半行缓存到下一块），不设行长上限；读取失败抛出带原因的异常，绝不静默结束。
     */
    private suspend fun readNdjsonFrames(channel: ByteReadChannel, onFrame: (String) -> Boolean) {
        val framer = VolcanoNdjsonFramer()
        val chunk = ByteArray(NDJSON_CHUNK_BYTES)
        while (true) {
            val read = try {
                channel.readAvailable(chunk, 0, chunk.size)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                throw IllegalStateException(
                    "火山 TTS 流式读取中断（${failure.javaClass.simpleName}: ${failure.message}）",
                    failure,
                )
            }
            if (read < 0) break
            if (read == 0) continue
            for (line in framer.feed(chunk, 0, read)) {
                if (onFrame(line)) return
            }
        }
        // 响应末尾没有换行符时，最后一行也要处理，否则会丢掉一个音频帧。
        framer.finish()?.let { onFrame(it) }
    }

    /**
     * 服务端排障 ID：火山在响应头返回 `X-Tt-Logid`，客服查单必须靠它。
     * 以前完全没记，玩家手里只有客户端自造的 request id，客服无法定位。
     */
    private fun responseLogId(response: HttpResponse): String =
        sequenceOf("X-Tt-Logid", "X-Tt-Log-Id", "X-Api-Request-Id", "X-Request-Id")
            .mapNotNull { response.headers[it]?.takeIf { value -> value.isNotBlank() } }
            .firstOrNull()
            .orEmpty()

    private companion object {
        const val MAX_TOTAL_CHARS = 800

        /** 流式读取块大小：够大以减少 syscall，又能让首个音频帧尽早到达播放器。 */
        const val NDJSON_CHUNK_BYTES = 16 * 1024
    }
}

/** 按顺序拼接音频分片；空列表返回空数组（调用方据此判断“空音频”）。 */
private fun concatAudio(chunks: List<ByteArray>): ByteArray {
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

/**
 * NDJSON 分帧器：把任意切分的 HTTP 响应字节流按 `\n` 切成一行行 JSON。
 *
 * 为什么不用 Ktor 的 `readUTF8Line()`：
 * - 它是按“字符数”计上限的读行 API，一旦被传了 max，实测单帧 3~100 KB 的音频帧会直接抛 TooLongLineException；
 * - 原来的调用点写成 `runCatching { readUTF8Line() }.getOrNull() ?: break`，
 *   任何读取异常都会被静默当成“流结束”，于是一帧音频都收不到，只剩一句没法排障的“返回空音频”。
 * 这里自己按字节分帧：不设行长上限，跨 chunk 的半行缓存到下一块，读取异常由调用方原样抛出。
 * 纯 Kotlin 实现，因此离线回归测试可以直接把真实响应体按任意块大小喂进来。
 */
class VolcanoNdjsonFramer {
    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    /** 追加 `bytes[offset, offset+length)`，返回其中所有完整的行（不含换行符，并已裁掉行尾 CR）。 */
    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): List<String> {
        val lines = ArrayList<String>()
        val end = offset + length
        var start = offset
        var index = offset
        while (index < end) {
            if (bytes[index] == NEWLINE) {
                append(bytes, start, index)
                lines += takeLine()
                start = index + 1
            }
            index++
        }
        append(bytes, start, end)
        return lines
    }

    /** 流结束时取出最后一行（响应末尾没有换行符的情况）；没有残留则返回 null。 */
    fun finish(): String? = if (size == 0) null else takeLine()

    private fun append(bytes: ByteArray, start: Int, end: Int) {
        if (end <= start) return
        ensure(size + (end - start))
        bytes.copyInto(buffer, size, start, end)
        size += end - start
    }

    private fun takeLine(): String {
        var end = size
        if (end > 0 && buffer[end - 1] == CARRIAGE_RETURN) end--
        val line = buffer.copyOfRange(0, end).decodeToString()
        size = 0
        return line
    }

    private fun ensure(capacity: Int) {
        if (capacity <= buffer.size) return
        var next = buffer.size
        while (next < capacity) next = next shl 1
        buffer = buffer.copyOf(next)
    }

    private companion object {
        const val INITIAL_CAPACITY = 8 * 1024
        const val NEWLINE: Byte = '\n'.code.toByte()
        const val CARRIAGE_RETURN: Byte = '\r'.code.toByte()
    }
}

/**
 * 流式帧累积器：把 NDJSON 帧里的音频按顺序拼起来，并记录计费字符数、帧数、是否收到结束帧。
 *
 * 生产路径（[VolcanoTtsGateway] 的流式读取）与离线回归测试共用这一个实现，
 * 所以测试断言的是真实解析逻辑，而不是测试里另写一份解析。
 */
class VolcanoTtsFrameCollector {
    private val audio = ArrayList<ByteArray>()

    /** 已处理的帧数（含空行以外的所有帧），用于“空音频”时报出到底读到了什么。 */
    var frameCount: Int = 0
        private set

    /** 是否收到 is_last=true 或 code=20000000/3000 的结束帧。 */
    var sawEndFrame: Boolean = false
        private set

    /** 结束帧里的 usage.text_words（火山按字符计费），没收到则为 null。 */
    var lastUsageChars: Int? = null
        private set

    /**
     * 消费一行 NDJSON。返回 true 表示收到结束帧、调用方可以停止读取。
     *
     * 业务错误帧（code 既不是 0 也不是结束码）会抛出可读错误；
     * [diagnostics] 只在抛错时求值，用来附带 HTTP 状态 / 资源版本 / 音色 / LogID 等排障信息。
     */
    fun accept(line: String, diagnostics: (serverRequestId: String) -> String = { "" }): Boolean {
        if (line.isBlank()) return false
        val frame = parseVolcanoTtsFrame(line)
        frameCount++
        if (frame.errorCode != null) {
            val detail = diagnostics(frame.requestId)
            error(volcanoTtsErrorMessage(frame.errorCode, frame.message, detail))
        }
        frame.usageChars?.let { lastUsageChars = it }
        frame.audioBase64?.let { encoded ->
            if (encoded.isNotBlank()) {
                val decoded = runCatching { encoded.decodeBase64Bytes() }.getOrElse { failure ->
                    error("火山 TTS 音频帧无法解码（${failure.javaClass.simpleName}: ${failure.message}）")
                }
                audio += decoded
            }
        }
        if (frame.isLast) sawEndFrame = true
        return frame.isLast
    }

    /** 按顺序拼接好的完整音频。 */
    fun audioBytes(): ByteArray = concatAudio(audio)
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
    /** 服务端在帧里回的 request_id（V3 信封），用于向客服定位这一次合成。 */
    val requestId: String = "",
)

/** 火山返回体的统一信封：旧版是顶层 code/message，V3 是 header.code / header.message / header.request_id。 */
data class VolcanoResponseEnvelope(
    val code: Int? = null,
    val message: String = "",
    val requestId: String = "",
)

private val volcanoJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 解析一行或一整段火山返回体。
 *
 * 同一个接口会返回两种信封：
 * - 旧版：`{"code":0,"message":"","data":{"audio":"..."}}`
 * - V3：`{"header":{"code":0,"message":"","request_id":"..."},"payload":{...}}`
 *
 * 错误只出现在 header 里时，只读顶层会得到 code=null，玩家就只看到“返回无法解析”。
 */
fun parseVolcanoResponseEnvelope(raw: String): VolcanoResponseEnvelope? =
    parseVolcanoEnvelopeObject(raw.trim().removePrefix("data:").trim())

private fun parseVolcanoEnvelopeObject(payload: String): VolcanoResponseEnvelope? {
    if (payload.isBlank()) return null
    val root = runCatching { volcanoJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
    return envelopeFromRoot(root)
}

private fun envelopeFromRoot(root: kotlinx.serialization.json.JsonObject): VolcanoResponseEnvelope {
    val header = root["header"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val code = sequenceOf(root["code"], root["status_code"], header?.get("code"))
        .mapNotNull { it?.stringOrNull()?.toIntOrNull() }
        .firstOrNull()
    val message = sequenceOf(
        root["message"], header?.get("message"),
        root["msg"], header?.get("msg"),
        root["error"], header?.get("error"),
        header?.get("error_message"), header?.get("err_msg"),
    ).mapNotNull { it?.stringOrNull() }.firstOrNull().orEmpty()
    val requestId = sequenceOf(
        root["request_id"], header?.get("request_id"),
        root["reqid"], header?.get("reqid"),
        root["log_id"], header?.get("log_id"),
    ).mapNotNull { it?.stringOrNull() }.firstOrNull().orEmpty()
    return VolcanoResponseEnvelope(code, message, requestId)
}

/** JsonElement 里只有 JsonPrimitive 能取字符串；对象/数组直接返回 null（之前用 jsonPrimitive 会抛异常）。 */
private fun kotlinx.serialization.json.JsonElement.stringOrNull(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

/**
 * 解析一行 NDJSON 帧。
 *
 * 按官方说明：`code=0` 是音频数据帧，`code=20000000`（旧文档写作 3000）是结束帧并可能带 `usage`，
 * 其它 code 一律当错误；`is_last=true` 也必须立刻停止读取（不要等连接断开）。
 * 音频可能在 `data.audio`（旧版）、`payload.audio`（V3）或 `data` 直接是 base64（更老的版本）。
 */
fun parseVolcanoTtsFrame(line: String): VolcanoTtsFrame {
    val trimmed = line.trim().removePrefix("data:").trim()
    if (trimmed.isBlank()) return VolcanoTtsFrame()
    val root = runCatching { volcanoJson.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        ?: return VolcanoTtsFrame()
    val envelope = envelopeFromRoot(root)
    val code = envelope.code
    val message = envelope.message
    val data = root["data"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val payload = root["payload"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val payloadData = payload?.get("data")?.let { runCatching { it.jsonObject }.getOrNull() }
    val audio = sequenceOf(
        data?.get("audio"),
        payload?.get("audio"),
        payloadData?.get("audio"),
        root["audio"],
    ).mapNotNull { it?.stringOrNull() }.firstOrNull()
        ?: sequenceOf(root["data"], payload?.get("data"))
            .mapNotNull { it?.stringOrNull()?.takeIf(::looksLikeBase64Audio) }
            .firstOrNull()
    val usage = root["usage"]?.let { runCatching { it.jsonObject }.getOrNull() }
        ?: payload?.get("usage")?.let { runCatching { it.jsonObject }.getOrNull() }
    val usageChars = usage?.get("text_words")?.stringOrNull()?.toIntOrNull()
    val isLast = sequenceOf(root["is_last"], payload?.get("is_last"))
        .mapNotNull { it?.stringOrNull()?.toBooleanStrictOrNull() }
        .firstOrNull() == true ||
        code == 20_000_000 || code == 3_000
    val isError = code != null && code != 0 && code != 20_000_000 && code != 3_000
    return VolcanoTtsFrame(
        audioBase64 = audio,
        isLast = isLast,
        usageChars = usageChars,
        errorCode = if (isError) code else null,
        message = message,
        requestId = envelope.requestId,
    )
}

/**
 * 排障详情：火山客服需要的字段一次凑齐（HTTP 状态、X-Api-Resource-Id、speaker、客户端请求 ID、服务端 LogID/request_id）。
 * 提示里带上这些，玩家就能直接把这一整行贴给客服，不用再来回问“哪个 ID”。
 */
fun volcanoDiagnosticDetail(
    httpStatus: Int,
    resourceId: String,
    speaker: String,
    clientRequestId: String,
    serverLogId: String = "",
    serverRequestId: String = "",
    streamed: Boolean = false,
): String = buildString {
    append(if (streamed) "流式返回" else "HTTP $httpStatus")
    if (streamed) append("，HTTP $httpStatus")
    append("；资源版本=${resourceId.ifBlank { "未设置" }}")
    append("；音色=${speaker.ifBlank { "未填写" }}")
    append("；客户端请求ID=${clientRequestId.ifBlank { "未生成" }}")
    if (serverLogId.isNotBlank()) append("；服务端LogID=$serverLogId")
    if (serverRequestId.isNotBlank() && serverRequestId != clientRequestId) append("；服务端请求ID=$serverRequestId")
}

/** 只有足够长且全是 base64 字符的字符串才当音频，避免把 message/status 之类的短文本误当音频解码。 */
private fun looksLikeBase64Audio(value: String): Boolean =
    value.length >= 64 && value.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }

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
 * [suffix] 由调用方拼上 HTTP 状态、资源版本、音色、请求 ID，这些都是客服要的第一手信息。
 */
fun volcanoTtsErrorMessage(code: Int?, message: String, suffix: String = ""): String {
    val raw = message.trim()
    val lower = raw.lowercase()
    val hint = when {
        code == null && raw.isBlank() ->
            "火山 TTS 没有返回任何内容：请检查网络、接口地址（默认 https://openspeech.bytedance.com/api/v3/tts/unidirectional）"
        code == null ->
            "火山 TTS 返回了无法识别的响应（既不是音频帧也不是标准错误体，常见于网关/鉴权层直接拦截）：${raw.take(400)}"
        code == 40_200_011 || code == 40_200_012 || lower.contains("qps") || lower.contains("concurrency") ->
            "火山 TTS 触发限流（并发或 QPS 超限），请稍后再试：$code $raw"
        // 实测（2026-10 真实 401）：{"header":{"reqid":"..","code":45000010,"message":"Invalid X-Api-Key"}}
        // 应用里“TTS 密钥留空会回退聊天密钥”，所以这个错误最常见的成因是把别家厂商的 Key 发给了火山。
        code == 45_000_010 || lower.contains("api-key") || lower.contains("apikey") || lower.contains("api key") ->
            "API Key 无效或未被识别（Invalid X-Api-Key）：请在 模型设置 > 文字转语音 TTS 里填写豆包语音控制台「API Key 管理」创建的那个 Key。" +
                "注意 TTS 密钥留空时应用会回退使用聊天模型的密钥，那种 Key 对火山无效（$code）"
        lower.contains("authentication") || lower.contains("no token") || lower.contains("access_key") ||
            lower.contains("access key") || lower.contains("grant not found") || lower.contains("unauthorized") ->
            "火山 TTS 鉴权失败：请在模型设置里重新填写控制台「API Key 管理」创建的 API Key，并确认这个 Key 所属应用已开通语音合成（$code）"
        // 官方文档：45000000 + "speaker permission denied: get resource id: access denied"
        // 表示 speaker 不存在/未授权，或 Resource-ID 与 speaker 不匹配（复刻音色却传了 seed-tts-*）。
        // 必须放在泛化的 access denied 之前，否则会被误判成“未购买资源包”。
        lower.contains("speaker permission") || (lower.contains("access denied") && lower.contains("resource id")) ->
            "音色鉴权失败：speaker 不存在或未授权，或 Resource-ID 与音色不匹配。复刻音色必须用 seed-icl-2.0 / seed-icl-1.0，不能传 seed-tts-*；也请确认这个 Speaker ID 属于当前 API Key 的账号（$code $raw）"
        lower.contains("access denied") || lower.contains("quota exceeded") || lower.contains("not activated") ||
            lower.contains("not subscribed") || lower.contains("unopened") || raw.contains("未开通") ->
            "火山 TTS 无资源权限或额度已用尽：请确认已开通「声音复刻」并购买对应资源包（$code）"
        lower.contains("mismatched") ->
            "音色与模型版本不匹配：2.0 复刻的音色要选 seed-icl-2.0，1.0 的要选 seed-icl-1.0（$code）"
        lower.contains("resource id") || lower.contains("resourceid") || lower.contains("resource_id") ->
            "资源版本（X-Api-Resource-Id）无效或与音色不匹配：请在模型设置里把复刻版本选成该音色所属的 seed-icl-1.0 / seed-icl-2.0（$code $raw）"
        lower.contains("app id") || lower.contains("appid") || lower.contains("app_id") ->
            "火山要求有效的 App ID：请在控制台确认 API Key 所属应用与音色属于同一个账号/应用（$code $raw）"
        lower.contains("invalid model") || lower.contains("invalidmodel") ->
            "模型名不被该资源版本接受：1.0 复刻音色不要传 model，2.0 用 seed-tts-2.0-standard（$code $raw）"
        code == 45_002_000 -> "音色 ID 为空：请在角色编辑页填写火山音色 ID（$code）"
        code == 45_000_001 && lower.contains("speaker") -> "音色不存在：请核对角色编辑页里的 Speaker ID 是否与控制台一致（$code）"
        code == 45_001_115 || code == 45_001_107 -> "音色已释放或不存在：后付费音色 7 天未合成会被释放，请到控制台重新训练（$code）"
        lower.contains("speaker") && (lower.contains("not found") || lower.contains("not exist") || lower.contains("invalid")) ->
            "音色不存在或不属于当前账号：请到控制台「音色库 - 我的音色」复制 Speaker ID（S_ 开头），注意复刻 1.0 / 2.0 要选对（$code $raw）"
        code == 40_402_003 || lower.contains("textlimit") -> "文本超过单次上限（约 300 字）：请缩短内容（$code）"
        code == 45_002_001 -> "这段内容没有可朗读的文本（$code）"
        code == 45_000_000 -> "火山 TTS 鉴权失败：请在模型设置里重新填写控制台「API Key 管理」创建的 API Key（$code）"
        lower.contains("timeout") -> "火山 TTS 请求超时，请稍后重试（$code）"
        code == 55_000_000 || code == 55_001_309 -> "火山 TTS 服务端错误，请稍后重试（$code $raw）"
        else -> "火山 TTS 错误 $code：${raw.take(200)}"
    }
    return if (suffix.isBlank()) hint else "$hint（$suffix）"
}
