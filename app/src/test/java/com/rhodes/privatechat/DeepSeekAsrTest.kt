package com.rhodes.privatechat
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.voice.*
import com.rhodes.privatechat.shared.modelgateway.*
import com.russhwolf.settings.ObservableSettings
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.net.InetSocketAddress

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class DeepSeekAsrTest {
    private fun repository(initial: Map<String, Any> = emptyMap()): SettingsRepository {
        val data = initial.toMutableMap()
        val backend = Proxy.newProxyInstance(ObservableSettings::class.java.classLoader,
            arrayOf(ObservableSettings::class.java)) { proxy, method, args ->
            val a = args.orEmpty()
            when {
                method.name == "hasKey" -> data.containsKey(a[0])
                method.name == "getKeys" -> data.keys.toSet()
                method.name == "getSize" -> data.size
                method.name.startsWith("get") -> data[a[0]] ?: a.getOrNull(1)
                method.name.startsWith("put") -> { data[a[0] as String] = a[1]; null }
                method.name == "remove" -> { data.remove(a[0]); null }
                method.name == "clear" -> { data.clear(); null }
                method.name == "equals" -> proxy === a[0]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> "TestSettings"
                else -> null
            }
        } as ObservableSettings
        return SettingsRepository(backend)
    }

    @Test fun newUsersRequireSuccessfulChatAndChangedCredentialsInvalidate() {
        val s=repository()
        s.apiKey="fake-key"; s.provider="deepseek"
        assertEquals("auto",s.asrMode)
        assertFalse(s.hasAsrConfiguration())
        s.markDeepSeekChatVerified(s.apiKey,s.modelName)
        assertTrue(s.hasAsrConfiguration())
        assertEquals("deepseek",s.effectiveAsrProvider())
        assertEquals(s.apiKey,s.effectiveAsrConfiguration().key)
        s.apiKey="different";assertFalse(s.hasAsrConfiguration())
        s.apiKey="fake-key";s.provider="openai";assertFalse(s.hasAsrConfiguration())
    }
    @Test fun manualSettingsWinAndLegacyConfigurationIsPreserved() {
        val s=repository(mapOf("asr_provider" to "ali", "asr_api_key" to "ali-key"))
        s.provider="deepseek";s.apiKey="fake";s.markDeepSeekChatVerified(s.apiKey,s.modelName)
        assertEquals("independent",s.asrMode)
        assertEquals("ali",s.effectiveAsrProvider())
        assertEquals("ali-key",s.effectiveAsrConfiguration().key)
        s.asrMode="auto";assertEquals("deepseek",s.effectiveAsrProvider())
        s.asrMode="independent";assertEquals("ali-key",s.effectiveAsrConfiguration().key)
    }
    @Test fun validatesDurationSampleRateAndResponse() {
        validateDeepSeekAudio(AsrRequest(ByteArray(16000*2*120)))
        assertThrows(IllegalArgumentException::class.java) { validateDeepSeekAudio(AsrRequest(ByteArray(16000*2*120+2))) }
        assertThrows(IllegalArgumentException::class.java) { validateDeepSeekAudio(AsrRequest(ByteArray(4),sampleRate=12000)) }
        assertEquals("你好",parseDeepSeekAsrText("""{"text":"你好","request_id":"id","duration_ms":200}"""))
        assertThrows(IllegalArgumentException::class.java) { parseDeepSeekAsrText("""{"text":null}""") }
    }
    @Test fun actualHttpMultipartContainsWavAndNoModelField() = runBlocking {
        val server=ServerSocket(0)
        var body=byteArrayOf();var auth="";var type=""
        val worker=thread {
            server.accept().use { socket ->
                val input=socket.getInputStream().buffered()
                fun line(): String {
                    val b=java.io.ByteArrayOutputStream()
                    while(true) { val n=input.read(); if(n<0 || n==10) break; if(n!=13)b.write(n) }
                    return b.toString("UTF-8")
                }
                line()
                val headers=mutableMapOf<String,String>()
                while(true) { val line=line(); if(line.isEmpty()) break; headers[line.substringBefore(":").lowercase()]=line.substringAfter(":").trim() }
                auth=headers["authorization"].orEmpty();type=headers["content-type"].orEmpty()
                body=ByteArray(headers["content-length"]!!.toInt())
                var read=0;while(read<body.size) { val n=input.read(body,read,body.size-read);if(n<0)break;read+=n }
                val result="""{"text":"协议测试成功","duration_ms":100}""".toByteArray()
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${result.size}\r\nConnection: close\r\n\r\n".toByteArray());write(result);flush() }
            }
        }
        try {
            val result=DeepSeekAsrGateway("http://127.0.0.1:${server.localPort}/v1/tools/asr","test-only").transcribe(AsrRequest(ByteArray(3200)))
            assertEquals("协议测试成功",result.text)
            assertEquals("Bearer test-only",auth)
            assertTrue(type.startsWith("multipart/form-data;"))
            val raw=body.toString(Charsets.ISO_8859_1)
            assertTrue(raw.contains("name=file") || raw.contains("name=\"file\""))
            assertTrue(raw.contains("RIFF"));assertTrue(raw.contains("WAVEfmt "))
            assertFalse(raw.contains("name=model"))
        } finally { server.close();worker.join(1000) }
    }
}
