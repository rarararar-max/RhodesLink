package com.rhodes.privatechat

import com.rhodes.privatechat.newspaper.*
import com.rhodes.privatechat.shared.settings.AtomicSettingsBatch
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.russhwolf.settings.ObservableSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import com.rhodes.privatechat.ui.support.AiSupportContract
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class NewspaperTest {
    private val date = "2026-10-20"
    private fun source(id: Int = 1, published: String = "2026-10-20T01:00:00Z") =
        NewsSource(id, "来源标题$id", "https://news.example.org/article/$id", "公开网站", published, "这是公开新闻的摘要。".repeat(12))
    private val sources get() = (1..4).map { source(it) }
    private fun articles() = (1..4).map { id ->
        NewspaperArticle(when (id) { 1 -> "今日头条"; 2 -> "技术与工造"; else -> "世间百态" },
            "第${id}项移动城市建设进展", "面向城际生活的公共报道", listOf("这是针对移动城市公共服务的一篇同人新闻。".repeat(9)), listOf(id))
    }
    private fun raw(items: List<NewspaperArticle> = articles()) =
        """{"articles":${newspaperJson.encodeToString(items)}}"""
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }

    private fun repository(balance: Int = 100, commitOk: Boolean = true, atomic: Boolean = true): SettingsRepository {
        val data = mutableMapOf<String, Any>("lmb" to balance)
        val interfaces = if (atomic) arrayOf(ObservableSettings::class.java, AtomicSettingsBatch::class.java)
            else arrayOf(ObservableSettings::class.java)
        val settings = Proxy.newProxyInstance(ObservableSettings::class.java.classLoader, interfaces) { proxy, method, args ->
            val a = args.orEmpty()
            when {
                method.name == "commitSupportState" -> {
                    if (commitOk) {
                        @Suppress("UNCHECKED_CAST") val strings = a[0] as Map<String, String>
                        @Suppress("UNCHECKED_CAST") val ints = a[1] as Map<String, Int>
                        data.putAll(strings); data.putAll(ints)
                    }
                    commitOk
                }
                method.name == "hasKey" -> data.containsKey(a[0])
                method.name == "getKeys" -> data.keys.toSet()
                method.name == "getSize" -> data.size
                method.name.startsWith("get") -> data[a[0]] ?: a.getOrNull(1)
                method.name.startsWith("put") -> { data[a[0] as String] = a[1]; null }
                method.name == "remove" -> { data.remove(a[0]); null }
                method.name == "equals" -> proxy === a[0]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> "TestNewspaperSettings"
                else -> null
            }
        } as ObservableSettings
        return SettingsRepository(settings)
    }

    @Test fun purchaseDebitsOnceAndPersistsTheMarkerTogether() {
        val settings = repository()
        repeat(3) { assertTrue(settings.purchaseNewspaper(date, 20)) }
        assertEquals(80, settings.lmb)
        assertTrue(settings.isNewspaperPurchased(date))
    }
    @Test fun insufficientFundsDoNotCreatePurchase() {
        val settings = repository(19)
        assertFalse(settings.purchaseNewspaper(date, 20))
        assertEquals(19, settings.lmb)
        assertFalse(settings.isNewspaperPurchased(date))
    }
    @Test fun failedAtomicCommitDoesNotDebitOrPublish() {
        val settings = repository(commitOk = false)
        assertFalse(settings.purchaseNewspaper(date, 20))
        assertEquals(100, settings.lmb)
        assertFalse(settings.isNewspaperPurchased(date))
    }
    @Test fun missingAtomicBackendCannotFallBackToSplitWrites() {
        val settings = repository(atomic = false)
        assertFalse(settings.purchaseNewspaper(date, 20))
        assertEquals(100, settings.lmb)
    }
    @Test fun invalidPurchaseArgumentsNeverCreditTheWallet() {
        val settings = repository()
        assertFalse(settings.purchaseNewspaper(date, -20))
        assertFalse(settings.purchaseNewspaper("../escape", 20))
        assertFalse(settings.purchaseNewspaper("2026-99-99", 20))
        assertEquals(100, settings.lmb)
    }
    @Test fun independentDatesArePurchasedSeparately() {
        val settings = repository()
        assertTrue(settings.purchaseNewspaper(date, 20))
        assertTrue(settings.purchaseNewspaper("2026-10-21", 20))
        assertEquals(60, settings.lmb)
    }
    @Test fun newFeatureDoesNotModifyExistingPersonaOrModelSettings() {
        val settings = repository()
        settings.putString("model_name", "user-model")
        settings.putString("prompt_group_director", "用户原文")
        settings.purchaseNewspaper(date, 20)
        assertEquals("user-model", settings.getString("model_name"))
        assertEquals("用户原文", settings.getString("prompt_group_director"))
    }
    @Test fun onlyOfficialDeepseekKeysCanBeReused() {
        assertEquals("test-key", reusableNewspaperKey("deepseek", "", "test-key"))
        assertEquals("test-key", reusableNewspaperKey("deepseek", "https://api.deepseek.com/v1/", "test-key"))
        assertEquals("", reusableNewspaperKey("ali", "", "test-key"))
        assertEquals("", reusableNewspaperKey("deepseek", "https://relay.example.org", "relay-key"))
        assertEquals("", reusableNewspaperKey("deepseek", "https://api.deepseek.com.evil.org", "test-key"))
    }
    @Test fun dateFilteringUsesBeijingCalendarRatherThanUtcDate() {
        assertTrue(sourceInWindow("2026-10-19T18:00:00Z", date, false))
        assertFalse(sourceInWindow("2026-10-20T18:00:00Z", date, false))
        assertFalse(sourceInWindow("2026-10-19T10:00:00Z", date, false))
        assertTrue(sourceInWindow("2026-10-19T10:00:00Z", date, true))
    }
    @Test fun unknownDatesAndOldItemsAreNotAdvertisedAsToday() {
        assertFalse(sourceInWindow("", date, true))
        assertFalse(sourceInWindow("2026-10-17T10:00:00Z", date, true))
        assertEquals(0, selectNewsSources(listOf(source(published = "")), date, false).size)
    }
    @Test fun urlAndTitleDuplicatesAreRemovedAndIdsAreReassigned() {
        val original = source(8)
        val duplicate = original.copy(url = original.url + "#comment")
        val sameTitle = original.copy(url = "https://news.example.org/different")
        val selected = selectNewsSources(listOf(original, duplicate, sameTitle, source(10)), date, false)
        assertEquals(listOf(1, 2), selected.map { it.id })
    }
    @Test fun unsafeLinksAreRejected() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "https://name:pass@example.org", "http://localhost",
            "http://127.0.0.1/a", "http://192.168.1.2/a", "http://10.0.0.1", "http://172.16.1.1").forEach {
            assertFalse(it, safeNewsUrl(it))
        }
    }
    @Test fun structuredArticlesRoundTripWithoutModelAuthoredUrls() {
        val result = parseNewspaperArticles(raw(), sources)
        assertEquals(4, result.size)
        assertEquals("今日头条", result.first().section)
        val edition = NewspaperEdition(date, result, sources, 100L)
        assertEquals(edition, newspaperJson.decodeFromString<NewspaperEdition>(newspaperJson.encodeToString(edition)))
    }
    @Test fun missingOrInventedSourceCannotBecomeAPaidPaper() {
        rejected { parseNewspaperArticles(raw(articles().map { it.copy(sourceIds = listOf(99)) }), sources) }
        rejected { parseNewspaperArticles(raw(articles().map { it.copy(sourceIds = emptyList()) }), sources) }
    }
    @Test fun truncatedAndEmptyArticlesAreRejected() {
        rejected { parseNewspaperArticles(raw(articles().take(2)), sources) }
        rejected { parseNewspaperArticles(raw(articles().map { it.copy(paragraphs = listOf("短")) }), sources) }
    }
    @Test fun duplicateHeadlinesAndMissingSectionsAreRejected() {
        rejected { parseNewspaperArticles(raw(articles().map { it.copy(title = "相同标题") }), sources) }
        rejected { parseNewspaperArticles(raw(articles().map { it.copy(section = "世间百态") }), sources) }
    }
    @Test fun htmlOrUrlsCannotBeRenderedAsNewsBody() {
        rejected { parseNewspaperArticles(raw(articles().map { it.copy(subtitle = "<script>alert(1)</script>") }), sources) }
        rejected { parseNewspaperArticles(raw(articles().map { it.copy(subtitle = "https://evil.example") }), sources) }
    }
    @Test fun realWorldOnlyReportingIsRejectedBeforePurchase() {
        rejected {
            parseNewspaperArticles(raw(articles().map {
                it.copy(title = "城市公共服务建设进展", paragraphs = listOf("这是一篇没有完成世界观改写的现实生活新闻报道。".repeat(8)))
            }.mapIndexed { index, article -> article.copy(title = "${index + 1}${article.title}") }), sources)
        }
    }
    @Test fun promptDefinesUntrustedMaterialsAndNoCharacterMemoryIntegration() {
        assertTrue(NEWSPAPER_SYSTEM_PROMPT.contains("不可信参考资料"))
        assertTrue(NEWSPAPER_SYSTEM_PROMPT.contains("不读取或写入"))
        assertTrue(NEWSPAPER_SYSTEM_PROMPT.contains("不让知名角色登场"))
        assertTrue(NEWSPAPER_SYSTEM_PROMPT.contains("不捏造新闻"))
    }

    @Test fun currentPriceIsTwoAndPurchaseRemainsIdempotent() {
        assertEquals(2, NewspaperService.PRICE)
        val settings = repository(2)
        repeat(2) { assertTrue(settings.purchaseNewspaper(date, NewspaperService.PRICE)) }
        assertEquals(0, settings.lmb)
    }
    @Test fun additionalCopiesAreIndependentButRetriesDoNotDoubleCharge() {
        val settings = repository(10)
        val first = "${date}_a1b2c3d4e5f6"
        val second = "${date}_123456abcdef"
        assertTrue(settings.purchaseNewspaper(date, 2))
        repeat(3) { assertTrue(settings.purchaseNewspaper(first, 2)) }
        assertTrue(settings.purchaseNewspaper(second, 2))
        assertEquals(4, settings.lmb)
        assertTrue(settings.isNewspaperPurchased(date))
        assertTrue(settings.isNewspaperPurchased(first))
        assertFalse(settings.purchaseNewspaper("${date}_../invalid", 2))
    }
    @Test fun oldEditionFilesRemainReadableWithoutIdOrTopicFields() {
        val json = buildJsonObject {
            put("date", date); put("createdAt", 100L)
            put("articles", newspaperJson.encodeToJsonElement(articles()))
            put("sources", newspaperJson.encodeToJsonElement(sources))
        }
        val edition = newspaperJson.decodeFromJsonElement<NewspaperEdition>(json)
        assertEquals(date, edition.id)
        assertEquals("GENERAL", edition.topic)
    }
    @Test fun topicsHaveIndependentQueriesAndNewFilesRespectRetention() {
        assertEquals(5, NewspaperTopic.entries.size)
        assertEquals(NewspaperTopic.GENERAL, NewspaperTopic.from("unknown"))
        assertTrue(NewspaperTopic.ENTERTAINMENT.searches.any { it.contains("娱乐") })
        assertTrue(NewspaperTopic.TECHNOLOGY.searches.any { it.contains("科技") })
        assertEquals(date, newspaperFileDate("edition-${date}_a1b2c3d4e5f6.json.bak"))
        assertEquals(date, newspaperFileDate("materials-${date}-false-ENTERTAINMENT.json"))
        assertFalse(validNewspaperId("${date}_../invalid"))
    }
    @Test fun retentionIncludesTodayAndPreviousTwentyNineDatesOnly() {
        assertTrue(retainedNewspaperDate("2026-10-20", date))
        assertTrue(retainedNewspaperDate("2026-09-21", date))
        assertFalse(retainedNewspaperDate("2026-09-20", date))
        assertFalse(retainedNewspaperDate("2026-10-21", date))
        assertFalse(retainedNewspaperDate("not-a-date", date))
    }
    @Test fun retentionOnlyRecognizesNewspaperOwnedFiles() {
        assertEquals("2026-09-20", newspaperFileDate("edition-2026-09-20.json.bak"))
        assertEquals("2026-09-20", newspaperFileDate("materials-2026-09-20-false.json.new"))
        listOf("chat.db", "edition-user.json", "../edition-2026-09-20.json", "settings.xml").forEach {
            assertNull(newspaperFileDate(it))
        }
    }
    @Test fun completeModelReplyIsAcceptedAndAbnormalRepliesHaveSafeErrors() {
        fun reply(content: String, finish: String = "stop") = buildJsonObject {
            putJsonArray("choices") { addJsonObject {
                put("finish_reason", finish)
                putJsonObject("message") { put("content", content) }
            } }
        }
        assertEquals(4, parseNewspaperReply(reply(raw()), sources).size)
        listOf(buildJsonObject {}, reply(""), reply("not-json-secret-string"), reply(raw(), "length"),
            reply(raw(), "content_filter"), buildJsonObject { put("choices", "wrong-type") }).forEach { response ->
            try { parseNewspaperReply(response, sources); fail("Expected safe content failure") }
            catch (error: NewspaperContentException) {
                assertTrue(error.message.orEmpty().contains("重试"))
                assertFalse(error.message.orEmpty().contains("secret-string"))
            }
        }
    }
    @Test fun moderateOverlengthIsReadableButUnboundedContentIsRejected() {
        val longer = articles().map { it.copy(title = "移动城市公共服务建设进展".repeat(4),
            paragraphs = listOf("这是一段移动城市的公共报道。".repeat(50))) }
            .mapIndexed { index, article -> article.copy(title = "${index + 1}${article.title}") }
        assertEquals(4, parseNewspaperArticles(raw(longer), sources).size)
        rejected { parseNewspaperArticles(raw(articles().map {
            it.copy(paragraphs = listOf("移动城市".repeat(400)))
        }), sources) }
    }
    @Test fun onlineSearchCallSitesStayInsideNewspaperModule() {
        val project = if (File("src/main").exists()) File(".") else File("app")
        val roots = listOf(File(project, "src/main/java"), File(project, "../shared/src/commonMain/kotlin"))
        val offenders = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { it.name != "NewspaperService.kt" }
            .filter { file -> listOf("tools/search", "enable_search", "web_search_options").any { file.readText().contains(it) } }
        assertTrue("Search escaped newspaper: $offenders", offenders.isEmpty())
    }
    @Test fun supportManualExplainsNewspaperCostRetentionAndFailures() {
        val project = if (File("src/main").exists()) File(".") else File("app")
        val manual = File(project, "src/main/assets/support/product_manual_zh.md").readText()
        val sections = manual.split(Regex("(?m)(?=^##\\s+)"))
        val result = AiSupportContract.localReference(sections, "报纸购买失败重试，保留几天，多少钱")
        assertTrue(result.contains("2龙门币"))
        assertTrue(result.contains("30天"))
        assertTrue(result.contains("重试"))
    }
}
