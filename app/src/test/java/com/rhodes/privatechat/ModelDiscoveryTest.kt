package com.rhodes.privatechat

import com.rhodes.privatechat.shared.network.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ModelDiscoveryTest {
    @Test fun endpointsPreserveServicePrefixesAndAcceptChatOrBaseUrls() {
        assertEquals("https://host.example/compatible-mode/v1/models", modelListEndpoint("https://host.example/compatible-mode/v1/chat/completions", "custom"))
        assertEquals("https://host.example/v1/models", modelListEndpoint("https://host.example/v1/", "custom"))
        assertEquals("https://host.example/v1/models", modelListEndpoint("https://host.example", "custom"))
        assertEquals("https://host.example/v1/models", modelListEndpoint("https://host.example/v1/models", "custom"))
        assertEquals("https://host.example/v1/models", modelListEndpoint("https://host.example/v1/messages", "anthropic"))
        assertEquals("https://host.example/v1beta/models", modelListEndpoint("https://host.example/v1beta/models", "google"))
        assertEquals("https://host.example/v1/chat/completions", normalizeChatEndpoint("https://host.example/v1"))
    }
    @Test fun invalidUrlsCannotBecomeRequests() {
        listOf("", "not-a-url", "file:///tmp/models", "https://user:pass@host/v1", "https://host/v1?key=secret", "https://host/v1#fragment").forEach {
            assertThrows(IllegalArgumentException::class.java) { modelListEndpoint(it, "custom") }
        }
    }
    @Test fun parsesDeduplicatedIdsAndRejectsChatOrErrorPayloads() {
        assertEquals(listOf("a", "vendor/b"), parseModelCatalog("""{"data":[{"id":"a"},{"id":"a"},{"id":"vendor/b"},{"other":"c"}]}""", "custom").ids)
        assertThrows(IllegalStateException::class.java) { parseModelCatalog("""{"choices":[]}""", "custom") }
        assertThrows(IllegalStateException::class.java) { parseModelCatalog("<html>login</html>", "custom") }
    }
    @Test fun googleFiltersNonChatModelsAndPreservesPaginationToken() {
        val page = parseModelCatalog("""{"models":[{"name":"models/gemini-chat","supportedGenerationMethods":["generateContent"]},{"name":"models/embed","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"two"}""", "google")
        assertEquals(listOf("gemini-chat"), page.ids)
        assertEquals("two", page.nextCursor)
    }
    @Test fun fetchUsesDraftKeyWithoutCallingCompletion() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val service = ModelDiscoveryService { url, headers ->
            requests += url to headers
            ModelCatalogResponse(200, """{"data":[{"id":"z"},{"id":"a"}]}""")
        }
        assertEquals(listOf("a", "z"), service.fetch("custom", "https://host/v1/chat/completions", "draft-key").ids)
        assertEquals("https://host/v1/models", requests.single().first)
        assertEquals("Bearer draft-key", requests.single().second["Authorization"])
    }
    @Test fun paginationUsesSameOriginAndEncodedTokens() = runBlocking {
        val urls = mutableListOf<String>()
        val service = ModelDiscoveryService { url, headers ->
            urls += url
            assertEquals("key", headers["x-goog-api-key"])
            val next = if (urls.size == 1) ",\"nextPageToken\":\"next+token\"" else ""
            ModelCatalogResponse(200, """{"models":[{"name":"models/chat${urls.size}","supportedGenerationMethods":["generateContent"]}]$next}""")
        }
        assertEquals(2, service.fetch("google", "https://host/v1beta/models", "key").ids.size)
        assertTrue(urls[1].contains("pageToken=next%2Btoken"))
    }
    @Test fun anthropicUsesNativeHeadersAndNextPage() = runBlocking {
        var calls = 0
        val service = ModelDiscoveryService { url, headers ->
            calls++
            assertEquals("key", headers["x-api-key"])
            assertEquals("2023-06-01", headers["anthropic-version"])
            if (calls == 2) assertTrue(url.contains("after_id=claude-a"))
            ModelCatalogResponse(200, if (calls == 1) """{"data":[{"id":"claude-a"}],"has_more":true,"last_id":"claude-a"}"""
                else """{"data":[{"id":"claude-b"}],"has_more":false}""")
        }
        assertEquals(listOf("claude-a", "claude-b"), service.fetch("anthropic", "https://host/v1/messages", "key").ids)
    }
    @Test fun emptyOrUnauthorizedCatalogKeepsManualFallbackAndNeverLeaksResponse() = runBlocking {
        val denied = ModelDiscoveryService { _, _ -> ModelCatalogResponse(401, "secret account response") }
        try { denied.fetch("custom", "https://host/v1", "key"); fail() } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("密钥")); assertFalse(e.message!!.contains("secret"))
        }
        val empty = ModelDiscoveryService { _, _ -> ModelCatalogResponse(200, """{"data":[]}""") }
        try { empty.fetch("custom", "https://host/v1", "key"); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("手动")) }
    }
    @Test fun cancellationPropagatesInsteadOfBecomingAnError() = runBlocking {
        val service = ModelDiscoveryService { _, _ -> throw CancellationException("cancelled") }
        try { service.fetch("custom", "https://host/v1", "key"); fail() } catch (_: CancellationException) { }
    }
    @Test fun repeatedPaginationTokenFailsInsteadOfLooping() = runBlocking {
        var calls = 0
        val service = ModelDiscoveryService { _, _ ->
            calls++
            ModelCatalogResponse(200, """{"models":[{"name":"models/a","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"same"}""")
        }
        try { service.fetch("google", "https://host/v1beta/models", "key"); fail() }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("分页")) }
        assertEquals(2, calls)
    }
    @Test fun xiaomiUsesItsOwnAuthenticationHeader() = runBlocking {
        val service = ModelDiscoveryService { _, headers ->
            assertEquals("key", headers["api-key"])
            assertFalse(headers.containsKey("Authorization"))
            ModelCatalogResponse(200, """{"data":[{"id":"mimo"}]}""")
        }
        assertEquals(listOf("mimo"), service.fetch("xiaomi", "https://host/v1/chat/completions", "key").ids)
    }
    @Test fun normalizationPreservesExistingNonstandardManualEndpoints() {
        assertEquals("https://host/api/custom-chat", normalizeChatEndpoint("https://host/api/custom-chat"))
        assertEquals("https://host/v1/chat/completions?tenant=abc", normalizeChatEndpoint("https://host/v1/chat/completions?tenant=abc"))
    }
}
