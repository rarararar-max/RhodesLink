package com.rhodes.privatechat

import com.rhodes.privatechat.shared.voice.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Local RFC6455 peer. No API keys, paid services or Internet are used. */
class DashScopeWireRegressionTest {
    private class Peer(private val socket: Socket) {
        private val input = socket.getInputStream()
        private val output = socket.getOutputStream()
        fun handshake() {
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) header.append(input.read().also { check(it >= 0) }.toChar())
            val key = header.lines().first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(":").trim()
            val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray()); output.flush()
        }
        fun receive(): String {
            val first = input.read(); check(first >= 0)
            val second = input.read(); var size = second and 127
            if (size == 126) size = (input.read() shl 8) or input.read()
            check(size < 65536)
            val mask = if (second and 128 != 0) ByteArray(4).also { input.readNBytes(it, 0, 4) } else ByteArray(4)
            val bytes = input.readNBytes(size)
            return ByteArray(size) { (bytes[it].toInt() xor mask[it % 4].toInt()).toByte() }.toString(Charsets.UTF_8)
        }
        fun send(text: String) = frame(1, text.toByteArray())
        fun frame(opcode: Int, bytes: ByteArray) {
            output.write(128 or opcode)
            if (bytes.size < 126) output.write(bytes.size) else { output.write(126); output.write(bytes.size shr 8); output.write(bytes.size and 255) }
            output.write(bytes); output.flush()
        }
        fun close() = frame(8, byteArrayOf(3, -24))
    }

    private fun runPeer(action: Peer.() -> Unit): Result<AsrResult> {
        ServerSocket(0).use { server ->
            val worker = Executors.newSingleThreadExecutor()
            val future = worker.submit {
                server.accept().use { socket -> socket.soTimeout = 5000; Peer(socket).apply { handshake(); action() } }
            }
            try {
                val result = runCatching { runBlocking {
                    withTimeout(10_000L) { createAsrGateway("ws://127.0.0.1:${server.localPort}/realtime?existing=1", "local-test-secret", "qwen3-asr-flash-realtime", "ali").transcribe(AsrRequest(ByteArray(3200))) }
                } }
                future.get(7, TimeUnit.SECONDS)
                return result
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun sessionIsConfiguredBeforeUploadAndFinalResultReturnsPromptly() {
        val start = System.nanoTime()
        val result = runPeer {
            assertTrue(receive().contains("session.update"))
            send("""{"type":"session.created"}""")
            send("""{"type":"session.updated"}""")
            assertTrue(receive().contains("input_audio_buffer.append"))
            assertTrue(receive().contains("input_audio_buffer.commit"))
            send("""{"type":"conversation.item.input_audio_transcription.text","text":"临时"}""")
            send("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"最终文字"}""")
            assertTrue(receive().contains("session.finish"))
            close()
        }
        assertEquals("最终文字", result.getOrThrow().text)
        assertTrue("Must not wait five seconds for session.finished", (System.nanoTime() - start) / 1_000_000 < 5000)
    }

    @Test fun structuredAccountErrorBeforeUploadIsVisibleAndRedacted() {
        val result = runPeer {
            receive()
            send("""{"type":"error","error":{"code":"AccessDenied","message":"denied local-test-secret"}}""")
            close()
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("AccessDenied"))
        assertFalse(result.exceptionOrNull()!!.message!!.contains("local-test-secret"))
    }

    @Test fun disconnectAfterPartialTextFailsInsteadOfReturningPartialText() {
        val result = runPeer {
            receive(); send("""{"type":"session.updated"}"""); receive(); receive()
            send("""{"type":"conversation.item.input_audio_transcription.text","text":"不能作为结果"}""")
            close()
        }
        assertTrue(result.isFailure)
    }

    @Test fun malformedLongCloseAfterFinalCannotDiscardCompletedResult() {
        val result = runPeer {
            receive(); send("""{"type":"session.updated"}"""); receive(); receive()
            send("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"已完成"}""")
            receive()
            frame(8, ByteArray(130) { 65 })
        }
        assertEquals("已完成", result.getOrThrow().text)
    }

    @Test fun malformedLongCloseBeforeFinalIsAnExplicitFailure() {
        val result = runPeer { receive(); frame(8, ByteArray(130) { 65 }) }
        assertTrue(result.isFailure)
    }
    @Test fun parentCancellationClosesWaitingConnection() {
        ServerSocket(0).use { server ->
            val worker = Executors.newSingleThreadExecutor()
            val ready = java.util.concurrent.CountDownLatch(1)
            val future = worker.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    Peer(socket).apply {
                        handshake(); receive(); ready.countDown()
                        // Cancellation must dispose the connection, without waiting for the ASR deadline.
                        runCatching { receive() }
                    }
                }
            }
            try {
                runBlocking {
                    val request = async(Dispatchers.IO) {
                        createAsrGateway("ws://127.0.0.1:${server.localPort}", "local-test-secret", "qwen3-asr-flash-realtime", "ali")
                            .transcribe(AsrRequest(ByteArray(3200)))
                    }
                    assertTrue(withContext(Dispatchers.IO) { ready.await(5, TimeUnit.SECONDS) })
                    request.cancel()
                    withTimeout(2000) { request.join() }
                    assertTrue(request.isCancelled)
                }
                future.get(5, TimeUnit.SECONDS)
            } finally { worker.shutdownNow() }
        }
    }

}
