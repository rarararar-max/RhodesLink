package com.rhodes.privatechat

import com.rhodes.privatechat.shared.voice.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VoiceReliabilityTest {
    @Test fun interimTextCannotBecomeSuccessfulResult() {
        val state = DashScopeTranscript()
        state.accept("""{"type":"conversation.item.input_audio_transcription.text","text":"半句话"}""")
        assertFalse(state.completed)
        assertThrows(IllegalStateException::class.java) { state.result() }
    }

    @Test fun finalTextAndEmotionArePreserved() {
        val state = DashScopeTranscript()
        state.accept("""{"type":"conversation.item.input_audio_transcription.text","emotion":"happy","text":"中间结果"}""")
        state.accept("""{"type":"conversation.item.input_audio_transcription.completed","transcript":" 完整结果 "}""")
        assertEquals(AsrResult("完整结果", "happy"), state.result())
    }

    @Test fun finalSilenceIsAValidEmptyResult() {
        val state = DashScopeTranscript()
        state.accept("""{"type":"conversation.item.input_audio_transcription.completed","transcript":""}""")
        assertEquals("", state.result().text)
    }

    @Test fun errorsAfterPartialTextAreNotSwallowed() {
        val state = DashScopeTranscript()
        state.accept("""{"type":"conversation.item.input_audio_transcription.text","text":"部分文字"}""")
        val error = assertThrows(IllegalStateException::class.java) {
            state.accept("""{"type":"error","error":{"code":"InvalidApiKey","message":"denied"}}""")
        }
        assertTrue(error.message!!.contains("InvalidApiKey"))
        assertFalse(state.completed)
    }

    @Test fun malformedOrPrematureCompletionIsRejected() {
        for (raw in listOf("not json", """{"type":"session.finished"}""", """{"type":"conversation.item.input_audio_transcription.completed"}""", """{"type":"error"}""")) {
            assertThrows(Exception::class.java) { DashScopeTranscript().accept(raw) }
        }
    }

    @Test fun informationalMessageAndNullErrorAreNotFailures() {
        assertEquals("session.updated", DashScopeTranscript().accept("""{"type":"session.updated","message":"ok","error":null}"""))
    }

    @Test fun invalidAudioIsRejectedBeforeNetworkEncoding() {
        for (request in listOf(AsrRequest(ByteArray(3)), AsrRequest(ByteArray(2), sampleRate = 0), AsrRequest(ByteArray(2), inputAudioFormat = "mp3"), AsrRequest(ByteArray(4_000_002)))) {
            assertThrows(IllegalArgumentException::class.java) { validateAsrAudio(request) }
        }
        validateAsrAudio(AsrRequest(ByteArray(3200)))
    }

    @Test fun ownedDeadlineIsFailureButParentCancellationRemainsCancellation() = runBlocking {
        try {
            withVoiceDeadline(20, "测试") { delay(1_000); "late" }
            fail("Expected timeout failure")
        } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("超时")) }
        val job = async { withVoiceDeadline(10_000, "测试") { delay(10_000); "late" } }
        yield()
        job.cancel()
        try { job.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }

    @Test fun ttsRejectsEmptyAudioAndRedactsCredentials() = runBlocking {
        val empty = ReliableTtsGateway(object : TtsGateway {
            override suspend fun synthesize(request: TtsRequest) = TtsResult(ByteArray(0))
        }, "secret-value")
        try { empty.synthesize(TtsRequest("测试", "voice")); fail("Expected empty audio rejection") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("音频")) }
        val failure = ReliableTtsGateway(object : TtsGateway {
            override suspend fun synthesize(request: TtsRequest): TtsResult = error("denied secret-value")
        }, "secret-value")
        try { failure.synthesize(TtsRequest("测试", "voice")); fail("Expected failure") }
        catch (e: IllegalStateException) { assertFalse(e.message!!.contains("secret-value")) }
    }
}
