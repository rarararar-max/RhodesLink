package com.rhodes.privatechat

import com.rhodes.privatechat.shared.voice.modelForResource
import com.rhodes.privatechat.shared.voice.parseVolcanoTtsFrame
import com.rhodes.privatechat.shared.voice.splitVolcanoTtsText
import com.rhodes.privatechat.shared.voice.volcanoTtsErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun modelIsSentOnlyForClonedV2() {
        assertEquals("seed-tts-2.0-standard", modelForResource("seed-icl-2.0"))
        // 1.0 下传 model 会报 InvalidModel，所以必须不传
        assertNull(modelForResource("seed-icl-1.0"))
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
}
