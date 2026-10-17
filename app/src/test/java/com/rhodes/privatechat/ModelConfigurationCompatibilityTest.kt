package com.rhodes.privatechat

import com.rhodes.privatechat.shared.model.*
import com.rhodes.privatechat.shared.modelgateway.*
import com.rhodes.privatechat.shared.network.ModelCapabilities
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.voice.*
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.russhwolf.settings.ObservableSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** An in-memory settings backend; no Android context, keys or persistent user data. */
@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class ModelConfigurationCompatibilityTest {
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

    private fun save(settings: SettingsRepository, provider: String = settings.asrProvider,
        config: VoiceProviderConfiguration = VoiceProviderConfiguration(settings.asrBaseUrl, settings.asrModelName, settings.asrApiKey),
        mode: String = settings.visionMode) {
        settings.saveModelConfiguration(settings.provider, settings.modelName, settings.customUrl, settings.apiKey,
            settings.visionBaseUrl, settings.visionProvider, settings.visionModelName, settings.visionApiKey,
            settings.vectorProviderMode, settings.vectorProvider, settings.vectorBaseUrl, settings.vectorModelName,
            settings.vectorApiKey, config.endpoint, provider, config.model, config.key,
            settings.ttsBaseUrl, settings.ttsProvider, settings.ttsModelName, settings.ttsApiKey,
            visionMode = mode)
    }

    @Test fun freshInstallUsesAutomaticVision() {
        assertEquals("auto", repository().visionMode)
    }

    @Test fun existingChatSettingsPreserveLegacyDefaultVision() {
        assertEquals("independent", repository(mapOf("api_key" to "test-chat-key")).visionMode)
    }

    @Test fun independentVisionSurvivesUpgradeAndSave() {
        val settings = repository(mapOf("vision_provider" to "deepseek", "vision_base_url" to "https://example.invalid/chat/completions",
            "vision_model_name" to "my-old-model", "vision_api_key" to "test-vision-key", "model_name" to "custom-text-model",
            "deepseek_thinking_enabled" to true))
        save(settings)
        assertEquals("independent", settings.visionMode)
        assertEquals("my-old-model", settings.visionModelName)
        assertEquals("test-vision-key", settings.visionApiKey)
        assertEquals("custom-text-model", settings.modelName)
        assertTrue(settings.deepseekThinkingEnabled)
        save(settings, mode = "auto")
        assertEquals("my-old-model", settings.visionModelName)
        assertEquals("test-vision-key", settings.visionApiKey)
    }

    @Test fun saveImmediatelyPersistsProfilesWithoutSavingAnOuterDraft() {
        val settings = repository(mapOf("asr_provider" to "ali", "asr_api_key" to "test-ali-key",
            "asr_base_url" to "wss://example.invalid/custom", "asr_model_name" to "custom-asr"))
        settings.beginDraft()
        settings.putString("unrelated", "draft")
        val xiaomi = defaultAsrConfiguration("xiaomi").copy(key = "test-xiaomi-key")
        save(settings, "xiaomi", xiaomi)
        settings.discardDraft()
        assertEquals("xiaomi", settings.asrProvider)
        val profiles = settings.voiceProviderConfigurations("asr")
        assertEquals("test-ali-key", profiles["ali"]?.key)
        assertEquals("wss://example.invalid/custom", profiles["ali"]?.endpoint)
        assertEquals(xiaomi, profiles["xiaomi"])
        assertEquals("", settings.getString("unrelated"))
    }

    @Test fun automaticVisionUsesChatKeyAndCompatibleAliEndpoint() {
        val config = resolveVisionConfiguration("auto", "ali", "qwen3.8-flash", "test-chat",
            "deepseek", "https://old.invalid", "test-old", "old-model")
        assertTrue(config.configured)
        assertEquals("test-chat", config.apiKey)
        assertEquals("ali", config.provider)
        assertTrue(config.endpoint.contains("/compatible-mode/"))
        assertTrue(createVisionGateway(config.provider, config.endpoint, config.apiKey, config.model) is OpenAiCompatVisionGateway)
    }

    @Test fun unverifiedModelNeverSilentlyUsesIndependentKey() {
        val config = resolveVisionConfiguration("auto", "deepseek", "deepseek-v4-pro", "test-chat",
            "ali", "https://old.invalid", "test-old", "old-model")
        assertFalse(config.configured)
        assertFalse(ModelCapabilities.supportsImages("custom", "deepseek-flash"))
    }

    @Test fun independentModeKeepsExplicitConfiguration() {
        val config = resolveVisionConfiguration("independent", "deepseek", "deepseek-flash", "test-chat",
            "ali", "https://old.invalid", "test-old", "old-model")
        assertEquals("test-old", config.apiKey)
        assertEquals("old-model", config.model)
    }

    @Test fun thinkingIsDisabledOnlyForSupportedModels() {
        assertEquals("disabled", ModelCapabilities.thinkingParameter("xiaomi", "mimo-v2.5")?.type)
        assertEquals("disabled", ModelCapabilities.thinkingParameter("zhipu", "glm-5.2")?.type)
        assertNull(ModelCapabilities.thinkingParameter("zhipu", "glm-5.3-flash"))
        assertNull(ModelCapabilities.thinkingParameter("zhipu", "glm-5.3"))
        assertNull(ModelCapabilities.thinkingParameter("custom", "my-model"))
        assertEquals(false, ModelCapabilities.enableThinkingParameter("ali", "qwen3.8-flash"))
        assertEquals("enabled", ModelCapabilities.thinkingParameter("deepseek", "deepseek-flash", true)?.type)
        assertEquals("disabled", ModelCapabilities.thinkingParameter("xiaomi", "mimo-v2.5", true)?.type)
    }

    @Test fun defaultTextRequestIsNonStreamingAndOmitsUnsupportedFlags() {
        val json = Json { encodeDefaults = true }
        val body = json.parseToJsonElement(json.encodeToString(ChatCompletionRequest(model = "test", messages = listOf(AiMessage("user", "hello"))))).jsonObject
        assertEquals(false, body["stream"]?.jsonPrimitive?.boolean)
        assertFalse(body.containsKey("thinking"))
        assertFalse(body.containsKey("enable_thinking"))
        val ali = json.parseToJsonElement(json.encodeToString(ChatCompletionRequest(messages = emptyList(), enable_thinking = false))).jsonObject
        assertEquals(false, ali["enable_thinking"]?.jsonPrimitive?.boolean)
    }

    @Test fun malformedAsrResponseDoesNotBecomeSuccessfulEmptyText() {
        try { parseMimoAsrText("""{"choices":[]}"""); fail("Missing choices must fail") }
        catch (_: IllegalStateException) {}
        assertEquals("今天下午三点", parseMimoAsrText("""{"choices":[{"message":{"content":" 今天下午三点 "}}]}"""))
        assertEquals("", parseMimoAsrText("""{"choices":[{"message":{"content":""}}]}"""))
    }

    @Test fun crossProviderAsrKeyFallbackIsVisible() {
        assertTrue(asrKeyFallbackHint("https://api.xiaomimimo.com/v1/chat/completions", "", "test-key",
            "deepseek", "xiaomi").orEmpty().contains("其它厂商"))
        assertNull(asrKeyFallbackHint("https://api.xiaomimimo.com/v1/chat/completions", "test-asr-key", "test-key",
            "deepseek", "xiaomi"))
        assertTrue(defaultAsrConfiguration("ali").endpoint.startsWith("wss://"))
    }

    @Test fun callCountIncludesFailedAttempts() {
        val usage = SharedUtils.ChatUsageSummary()
        usage.recordAttempt()
        usage.recordAttempt()
        assertTrue(usage.summary().contains("模型调用=2 次"))
    }
}
