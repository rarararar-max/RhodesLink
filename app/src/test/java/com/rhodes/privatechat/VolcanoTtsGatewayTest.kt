package com.rhodes.privatechat

import com.rhodes.privatechat.shared.voice.VolcanoNdjsonFramer
import com.rhodes.privatechat.shared.voice.VolcanoTtsFrameCollector
import com.rhodes.privatechat.shared.voice.maskedApiKey
import com.rhodes.privatechat.shared.voice.modelForResource
import com.rhodes.privatechat.shared.voice.parseVolcanoResponseEnvelope
import com.rhodes.privatechat.shared.voice.parseVolcanoTtsFrame
import com.rhodes.privatechat.shared.voice.splitVolcanoTtsText
import com.rhodes.privatechat.shared.voice.volcanoDiagnosticDetail
import com.rhodes.privatechat.shared.voice.volcanoTtsErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * 火山引擎（豆包语音）V3 单向流式合成的解析规则。
 *
 * 这些规则来自官方答复，写错就会表现为"没有声音"或"报错看不懂"：
 * 响应是 NDJSON 每行一帧、音频在 data.audio（base64）、结束帧 code=20000000（旧文档 3000）或 is_last=true、
 * 单次文本上限 1024 字节（中文约 300 字）超限报 40402003、1.0 音色不能传 model。
 */
class VolcanoTtsGatewayTest {

    @Test
    fun audioFrameCarriesBase64Audio() {
        val frame = parseVolcanoTtsFrame("""{"code":0,"data":{"audio":"SUQzBA=="},"is_last":false,"message":"success","sequence":0}""")
        assertEquals("SUQzBA==", frame.audioBase64)
        assertFalse(frame.isLast)
        assertNull(frame.errorCode)
    }

    @Test
    fun bothDocumentedEndCodesStopTheStream() {
        assertTrue(parseVolcanoTtsFrame("""{"code":20000000,"message":"ok","usage":{"text_words":123}}""").isLast)
        assertTrue(parseVolcanoTtsFrame("""{"code":3000,"data":{"audio":""},"is_last":true,"sequence":-1}""").isLast)
        // 结束帧必须带出计费字符数，供调试面板展示
        assertEquals(123, parseVolcanoTtsFrame("""{"code":20000000,"usage":{"text_words":123}}""").usageChars)
    }

    @Test
    fun businessErrorsInsideTheStreamAreDetected() {
        val mismatch = parseVolcanoTtsFrame("""{"code":55000000,"message":"resource ID is mismatched with speaker related resource"}""")
        assertEquals(55_000_000, mismatch.errorCode)
        assertTrue(volcanoTtsErrorMessage(mismatch.errorCode, mismatch.message).contains("音色与模型版本不匹配"))
        assertTrue(
            volcanoTtsErrorMessage(40402003, "TTSExceededTextLimit:exceed max limit").contains("超过单次上限"),
        )
        assertTrue(
            volcanoTtsErrorMessage(45_000_000, "no token or access_key was found").contains("鉴权失败"),
        )
        assertTrue(
            volcanoTtsErrorMessage(45_001_115, "speaker S_x not found in speaker_map").contains("已释放"),
        )
    }

    @Test
    fun blankAndDataPrefixedLinesAreTolerated() {
        assertNull(parseVolcanoTtsFrame("").errorCode)
        assertNull(parseVolcanoTtsFrame("   ").errorCode)
        assertEquals("SUQzBA==", parseVolcanoTtsFrame("data: {\"code\":0,\"data\":{\"audio\":\"SUQzBA==\"}}").audioBase64)
    }

    /**
     * V3 的错误体是 {"header":{"code":..,"message":..,"request_id":..}}。
     * 只读顶层 code/message 会得到 null，玩家只能看到“火山 TTS 返回无法解析：{header:{reqi...”这种提示，
     * 真实原因（音色不匹配 / 未开通 / 鉴权失败）和客服要的请求 ID 全丢了。
     */
    @Test
    fun v3HeaderEnvelopeCarriesTheRealErrorCodeAndRequestId() {
        val body = """{"header":{"code":45000000,"message":"no token or access_key was found","request_id":"req-abc-123"},"payload":{}}"""
        val envelope = parseVolcanoResponseEnvelope(body)
        assertEquals(45_000_000, envelope?.code)
        assertEquals("req-abc-123", envelope?.requestId)
        assertTrue(volcanoTtsErrorMessage(envelope?.code, envelope?.message.orEmpty()).contains("鉴权失败"))

        val mismatched = parseVolcanoResponseEnvelope(
            """{"header":{"code":55000000,"message":"resource ID is mismatched with speaker related resource","request_id":"req-2"}}"""
        )
        assertEquals(55_000_000, mismatched?.code)
        assertTrue(volcanoTtsErrorMessage(mismatched?.code, mismatched?.message.orEmpty()).contains("音色与模型版本不匹配"))
    }

    @Test
    fun v3HeaderEnvelopeFramesAreParsedForAudioErrorsAndUsage() {
        val audio = parseVolcanoTtsFrame("""{"header":{"code":0,"message":"Success","request_id":"req-1"},"payload":{"audio":"SUQzBA=="}}""")
        assertEquals("SUQzBA==", audio.audioBase64)
        assertNull(audio.errorCode)
        assertFalse(audio.isLast)

        val error = parseVolcanoTtsFrame("""{"header":{"code":55000000,"message":"resource ID is mismatched with speaker related resource","request_id":"req-2"}}""")
        assertEquals(55_000_000, error.errorCode)
        assertEquals("resource ID is mismatched with speaker related resource", error.message)

        val last = parseVolcanoTtsFrame("""{"header":{"code":20000000,"message":"ok","request_id":"req-3"},"payload":{"usage":{"text_words":9}}}""")
        assertTrue(last.isLast)
        assertEquals(9, last.usageChars)
    }

    @Test
    fun olderFlatBase64DataFramesStillYieldAudio() {
        val legacy = "SUQz" + "BA==".repeat(20)
        assertEquals(legacy, parseVolcanoTtsFrame("""{"code":0,"data":"$legacy","sequence":0}""").audioBase64)
        // 短文本（message/status 之类）不能当音频，否则会被误判成解码失败
        assertNull(parseVolcanoTtsFrame("""{"code":0,"data":"Success"}""").audioBase64)
    }

    @Test
    fun unrecognizedBodyKeepsRawTextForSupport() {
        val hint = volcanoTtsErrorMessage(null, """{"header":{"reqi""", "HTTP 502；请求ID=req-9")
        assertTrue(hint.contains("无法识别"))
        assertTrue(hint.contains("HTTP 502"))
        assertTrue(hint.contains("请求ID=req-9"))
        assertTrue(hint.contains("header"))
    }

    /**
     * 实测响应（2026-10-08 对真实端点发无效 Key 得到的 401 响应体）：
     * `{"header":{"reqid":"<客户端 X-Api-Request-Id 原样回显>","code":45000010,"message":"Invalid X-Api-Key"}}`
     * 这正是玩家截图里 `{"header":{"reqi...` 那种“看不懂”的报错。
     */
    @Test
    fun realEdgeAuthRejectionIsExplained() {
        val body = """{"header":{"reqid":"8bc20e6a-4fe1-43a8-a96b-56b5bd9d2a02","code":45000010,"message":"Invalid X-Api-Key"}}"""
        val envelope = parseVolcanoResponseEnvelope(body)
        assertEquals(45_000_010, envelope?.code)
        assertEquals("Invalid X-Api-Key", envelope?.message)
        assertEquals("8bc20e6a-4fe1-43a8-a96b-56b5bd9d2a02", envelope?.requestId)

        val hint = volcanoTtsErrorMessage(envelope?.code, envelope?.message.orEmpty())
        assertTrue(hint.contains("API Key"))
        assertTrue(hint.contains("无效"))
        assertTrue("必须提醒回退聊天密钥这个坑", hint.contains("回退"))
    }

    @Test
    fun modelIsSentOnlyForClonedV2() {
        assertEquals("seed-tts-2.0-standard", modelForResource("seed-icl-2.0"))
        // 1.0 下传 model 会报 InvalidModel，所以必须不传
        assertNull(modelForResource("seed-icl-1.0"))
    }

    /**
     * 官方错误码表：45000000 既可能是并发超限，也可能是 "speaker permission denied"。
     * 前者要提示限流，后者要提示 Resource-ID 与音色不匹配（复刻音色传成了 seed-tts-*）。
     */
    @Test
    fun sameCodeIsExplainedByItsMessage() {
        val denied = volcanoTtsErrorMessage(45_000_000, "speaker permission denied: get resource id: access denied")
        assertTrue(denied.contains("Resource-ID 与音色不匹配"))
        assertTrue(denied.contains("seed-icl-2.0"))

        val concurrency = volcanoTtsErrorMessage(45_000_000, "quota exceeded for types: concurrency")
        assertTrue(concurrency.contains("限流"))
    }

    /** 客服查单要 LogID：detail 必须把 HTTP 状态、资源版本、音色、客户端请求 ID、服务端 LogID 一次带全。 */
    @Test
    fun diagnosticDetailCarriesEverythingSupportNeeds() {
        val detail = volcanoDiagnosticDetail(
            httpStatus = 403,
            resourceId = "seed-icl-2.0",
            speaker = "S_abc123",
            clientRequestId = "client-req-1",
            serverLogId = "20260101120000ABCD",
            serverRequestId = "server-req-2",
        )
        assertTrue(detail.contains("HTTP 403"))
        assertTrue(detail.contains("资源版本=seed-icl-2.0"))
        assertTrue(detail.contains("音色=S_abc123"))
        assertTrue(detail.contains("客户端请求ID=client-req-1"))
        assertTrue(detail.contains("服务端LogID=20260101120000ABCD"))
        assertTrue(detail.contains("服务端请求ID=server-req-2"))

        // 服务端回显的 request_id 与客户端相同时不重复写
        val streamed = volcanoDiagnosticDetail(
            httpStatus = 200,
            resourceId = "",
            speaker = "",
            clientRequestId = "same-id",
            serverRequestId = "same-id",
            streamed = true,
        )
        assertTrue(streamed.startsWith("流式返回"))
        assertTrue(streamed.contains("资源版本=未设置"))
        assertTrue(streamed.contains("音色=未填写"))
        assertFalse(streamed.contains("服务端请求ID"))
    }

    /** 日志里永远不出现完整 API Key，但前缀+长度足够在控制台 Key 列表里对上号。 */
    @Test
    fun apiKeyIsOnlyLoggedAsAFingerprint() {
        val key = "sk-abcdefghijklmnopqrst"
        val masked = maskedApiKey(key)
        assertTrue(masked.startsWith("sk-abc"))
        assertFalse(masked.contains(key))
        assertEquals("未填写", maskedApiKey("   "))
        assertTrue(maskedApiKey("short").contains("过短"))
    }

    @Test
    fun longTextIsSplitUnderTheProviderLimit() {
        val long = "第一句话。".repeat(60) + "最后一句没有标点结尾"
        val parts = splitVolcanoTtsText(long)
        assertTrue("长文本必须被切分", parts.size > 1)
        parts.forEach { part ->
            assertTrue("每段不能超过 280 字: ${part.length}", part.length <= 280)
            assertTrue("每段不能超过 1000 字节: ${part.encodeToByteArray().size}", part.encodeToByteArray().size <= 1_000)
        }
        // 切分不能丢字
        assertEquals(long.trim().replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }

    @Test
    fun shortTextStaysInOnePiece() {
        assertEquals(listOf("你好，欢迎使用。"), splitVolcanoTtsText("你好，欢迎使用。"))
        assertTrue(splitVolcanoTtsText("   ").isEmpty())
    }

    /**
     * 回归：2026-10-09 对真实端点实测的响应体裁剪。
     *
     * 真实形状是「每行一帧、音频是顶层 `data` 的 base64 字符串」，`data` 为 null 的句子帧在中间、
     * 结束帧 `code=20000000` 带 `usage.text_words`。旧实现只认 `data.audio`，于是每一帧都取不到音频，
     * 拼出来 0 字节，App 上就只看到一句“火山 TTS 返回空音频”。
     * 这里用同样的帧形状断言能拼出实测的 22701 字节。
     */
    @Test
    fun realUnidirectionalStreamAssemblesEveryTopLevelDataFrame() {
        // 各帧 base64 解码后的真实字节数（实测 8 个音频帧 + 1 个句子帧 + 1 个结束帧 = 10 帧）
        val perFrameBytes = listOf(2349, 2496, 3840, 3264, 3072, 3264, 3072, 1344)
        val body = buildString {
            perFrameBytes.forEach { size ->
                append("""{"code":0,"message":"","data":"${base64OfDecodedSize(size)}"}""").append('\n')
            }
            append("""{"code":0,"message":"","data":null,"sentence":{"phonemes":[],"text":"你好，这是一次语音合成测试。","words":[]}}""").append('\n')
            append("""{"code":20000000,"message":"OK","data":null,"usage":{"text_words":14}}""").append('\n')
        }.toByteArray()

        val collector = collectStream(body, chunkSize = body.size)
        assertEquals("实测共 10 帧", 10, collector.frameCount)
        assertTrue("必须识别出结束帧", collector.sawEndFrame)
        assertEquals("实测音频总长 22701 字节", 22701, collector.audioBytes().size)
        assertEquals(14, collector.lastUsageChars)

        // 单帧也必须能取到 base64（旧实现这里返回 null，就是“空音频”的直接原因）
        val firstFrame = """{"code":0,"message":"","data":"${base64OfDecodedSize(2349)}"}"""
        assertEquals(base64OfDecodedSize(2349), parseVolcanoTtsFrame(firstFrame).audioBase64)
        assertFalse(parseVolcanoTtsFrame(firstFrame).isLast)
    }

    /**
     * 回归：音频帧单行可达几十~几百 KB，分块边界可能落在一行中间。
     * 逐字节/小块/整块读取必须得到完全相同的帧序列——这正是旧的 `readUTF8Line()` + `runCatching{}.getOrNull() ?: break`
     * 会静默丢帧的场景。
     */
    @Test
    fun hugeAudioFramesSurviveAnyChunkBoundary() {
        val hugeFrame = """{"code":0,"message":"","data":"${base64OfDecodedSize(60_000)}"}"""
        val endFrame = """{"code":20000000,"message":"OK","data":null,"usage":{"text_words":9}}"""
        val body = "$hugeFrame\r\n$endFrame\n".toByteArray()

        listOf(1, 7, 1024, 4096, body.size).forEach { chunkSize ->
            val lines = splitLines(body, chunkSize)
            assertEquals("块大小 $chunkSize 时分帧结果必须一致", listOf(hugeFrame, endFrame), lines)
            val collector = collectStream(body, chunkSize)
            assertEquals("块大小 $chunkSize 时音频必须完整", 60_000, collector.audioBytes().size)
            assertEquals(2, collector.frameCount)
            assertTrue(collector.sawEndFrame)
        }
        // 末尾没有换行符时最后一行也不能丢
        val noTrailingNewline = "$hugeFrame\n$endFrame".toByteArray()
        assertEquals(60_000, collectStream(noTrailingNewline, 512).audioBytes().size)
    }

    /** 错误帧必须抛出可读错误（带排障字段），而不是被吞掉变成“空音频”。 */
    @Test
    fun frameCollectorTurnsBusinessErrorFramesIntoReadableErrors() {
        val collector = VolcanoTtsFrameCollector()
        val error = runCatching {
            collectStream(
                """{"code":45000010,"message":"Invalid X-Api-Key","request_id":"req-7"}""".toByteArray(),
                chunkSize = 8,
                collector = collector,
                diagnostics = { serverRequestId -> "服务端请求ID=$serverRequestId" },
            )
        }.exceptionOrNull()
        val message = error?.message.orEmpty()
        assertTrue(message.contains("API Key"))
        assertTrue("错误信息必须带排障字段：$message", message.contains("服务端请求ID=req-7"))

        // data 为 null 的句子帧不产生音频，也不算结束
        val quiet = VolcanoTtsFrameCollector().accept("""{"code":0,"message":"","data":null,"sentence":{"text":"你好"}}""")
        assertFalse(quiet)
    }

    /** 按 [chunkSize] 把响应体喂进生产用的分帧器（[VolcanoNdjsonFramer]），取出所有行。 */
    private fun splitLines(body: ByteArray, chunkSize: Int): List<String> {
        val framer = VolcanoNdjsonFramer()
        val lines = ArrayList<String>()
        var offset = 0
        while (offset < body.size) {
            val length = minOf(chunkSize, body.size - offset)
            lines += framer.feed(body, offset, length)
            offset += length
        }
        framer.finish()?.let { lines += it }
        return lines
    }

    /** 走生产路径的分帧 + 帧累积逻辑：分块喂入 NDJSON，返回累积器。 */
    private fun collectStream(
        body: ByteArray,
        chunkSize: Int,
        collector: VolcanoTtsFrameCollector = VolcanoTtsFrameCollector(),
        diagnostics: (String) -> String = { "" },
    ): VolcanoTtsFrameCollector {
        val framer = VolcanoNdjsonFramer()
        var offset = 0
        while (offset < body.size) {
            val length = minOf(chunkSize, body.size - offset)
            for (line in framer.feed(body, offset, length)) {
                if (collector.accept(line, diagnostics)) return collector
            }
            offset += length
        }
        framer.finish()?.let { collector.accept(it, diagnostics) }
        return collector
    }

    /** 造一段 base64，使其解码后正好是 [bytes] 字节（长度与真实帧一致）。 */
    private fun base64OfDecodedSize(bytes: Int): String =
        Base64.getEncoder().encodeToString(ByteArray(bytes) { index -> (index % 251).toByte() })
}
