package com.rhodes.privatechat.shared.network

import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/** Enumeration never calls a completion endpoint or persists credentials. */
class ModelDiscoveryService(
    private val transport: suspend (String, Map<String, String>) -> ModelCatalogResponse = ::fetchModelCatalog,
) {
    suspend fun fetch(provider: String, endpoint: String, apiKey: String): ModelCatalog {
        require(apiKey.trim().isNotEmpty()) { "请先填写 API 密钥" }
        val url = modelListEndpoint(endpoint, provider)
        val headers = when (provider) {
            "xiaomi" -> mapOf("api-key" to apiKey.trim())
            "google" -> mapOf("x-goog-api-key" to apiKey.trim())
            "anthropic" -> mapOf("x-api-key" to apiKey.trim(), "anthropic-version" to "2023-06-01")
            else -> mapOf("Authorization" to "Bearer ${apiKey.trim()}")
        }
        return withTimeout(20_000) {
            val models = linkedSetOf<String>()
            var cursor = ""
            val seen = mutableSetOf<String>()
            repeat(10) {
                val requestUrl = URLBuilder(url).apply {
                    if (provider == "google") {
                        parameters.append("pageSize", "100")
                        if (cursor.isNotBlank()) parameters.append("pageToken", cursor)
                    } else if (provider == "anthropic") {
                        parameters.append("limit", "100")
                        if (cursor.isNotBlank()) parameters.append("after_id", cursor)
                    }
                }.buildString()
                val response = transport(requestUrl, headers)
                if (response.status !in 200..299) throw IllegalStateException(modelCatalogError(response.status))
                val page = parseModelCatalog(response.body, provider)
                models += page.ids
                if (models.size >= 1000) return@withTimeout ModelCatalog(models.take(1000), true)
                cursor = page.nextCursor
                if (cursor.isBlank()) {
                    require(models.isNotEmpty()) { "接口没有返回可选择的模型，请手动填写模型名称" }
                    return@withTimeout ModelCatalog(models.sorted())
                }
                require(seen.add(cursor)) { "模型列表分页异常，请手动填写模型名称" }
            }
            ModelCatalog(models.sorted(), true)
        }
    }
}

data class ModelCatalog(val ids: List<String>, val truncated: Boolean = false)
data class ModelCatalogResponse(val status: Int, val body: String)
data class ModelCatalogPage(val ids: List<String>, val nextCursor: String = "")

fun modelListEndpoint(endpoint: String, provider: String): String {
    require(endpoint.trim().startsWith("https://", true) || endpoint.trim().startsWith("http://", true)) { "API 地址需以 http:// 或 https:// 开头" }
    val parsed = runCatching { Url(endpoint.trim()) }.getOrElse { throw IllegalArgumentException("API 地址格式不正确") }
    require(parsed.protocol.name in setOf("http", "https") && parsed.host.isNotBlank()) { "API 地址需以 http:// 或 https:// 开头" }
    require(parsed.user == null && parsed.password == null && parsed.parameters.isEmpty() && parsed.fragment.isBlank()) { "请使用不含账号、查询参数和片段的 API 地址" }
    var path = parsed.encodedPath.trimEnd('/')
    path = when {
        path.endsWith("/chat/completions") -> path.removeSuffix("/chat/completions") + "/models"
        provider == "anthropic" && path.endsWith("/messages") -> path.removeSuffix("/messages") + "/models"
        path.endsWith("/models") -> path
        path.isBlank() -> "/v1/models"
        else -> "$path/models"
    }
    return URLBuilder(parsed).apply { encodedPath = path }.buildString()
}

/** Custom settings accept either a base URL or a complete chat URL. */
fun normalizeChatEndpoint(endpoint: String): String {
    val value = endpoint.trim()
    val url = runCatching { Url(value) }.getOrNull() ?: return value
    val path = url.encodedPath.trimEnd('/')
    // Preserve existing vendor-specific complete endpoints and query strings for manual use.
    if (url.parameters.isEmpty() && url.fragment.isBlank() &&
        (path.isBlank() || path.endsWith("/models") || Regex("/v[0-9]+(?:beta[0-9]*)?$").containsMatchIn(path))) {
        return modelListEndpoint(value, "custom").removeSuffix("/models") + "/chat/completions"
    }
    return value
}

fun parseModelCatalog(body: String, provider: String): ModelCatalogPage {
    val root = runCatching { Json.parseToJsonElement(body).jsonObject }
        .getOrElse { throw IllegalStateException("接口返回的不是模型列表，请检查 API 地址或手动填写模型名称") }
    val entries = root[if (provider == "google") "models" else "data"] as? JsonArray
        ?: throw IllegalStateException("此接口不支持获取模型列表，请手动填写模型名称")
    val ids = entries.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        if (provider == "google") {
            val methods = obj["supportedGenerationMethods"] as? JsonArray
            if (methods?.any { (it as? JsonPrimitive)?.contentOrNull == "generateContent" } != true) return@mapNotNull null
        }
        (obj[if (provider == "google") "name" else "id"] as? JsonPrimitive)?.contentOrNull
            ?.removePrefix(if (provider == "google") "models/" else "")?.trim()?.takeIf { it.isNotBlank() && it.length <= 512 }
    }.distinct()
    val next = when (provider) {
        "google" -> (root["nextPageToken"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        "anthropic" -> if ((root["has_more"] as? JsonPrimitive)?.booleanOrNull == true)
            (root["last_id"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("模型列表缺少分页信息，请手动填写模型名称") else ""
        else -> ""
    }
    return ModelCatalogPage(ids, next)
}

fun modelCatalogError(status: Int): String = when (status) {
    401 -> "API 密钥无效或已过期，请检查后重试"
    403 -> "当前密钥无权获取模型列表，可以手动填写后测试"
    404, 405 -> "该地址不支持获取模型列表，请检查地址或手动填写模型名称"
    429 -> "请求过于频繁或额度不足，请稍后重试"
    in 300..399 -> "模型接口发生重定向，请填写服务商提供的最终 API 地址"
    else -> "获取模型失败（HTTP $status），请稍后重试或手动填写模型名称"
}

private suspend fun fetchModelCatalog(url: String, headers: Map<String, String>): ModelCatalogResponse {
    val client = createHttpClient {
        followRedirects = false
        expectSuccess = false
    }
    try {
        val response = client.get(url) {
            timeout { requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000 }
            headers.forEach { (key, value) -> header(key, value) }
        }
        if (response.status.value !in 200..299) return ModelCatalogResponse(response.status.value, "")
        val bytes = response.bodyAsChannel().readRemaining(1_048_577).readBytes()
        require(bytes.size <= 1_048_576) { "模型列表过大，请手动填写模型名称" }
        return ModelCatalogResponse(response.status.value, bytes.decodeToString())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (_: Exception) {
        // Do not surface raw transport messages: they can contain addresses or credentials.
        throw IllegalStateException("无法连接模型列表接口，请检查网络和 API 地址后重试")
    } finally { client.close() }
}
