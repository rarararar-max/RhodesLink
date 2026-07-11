package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.DispatchRecord
import com.rhodes.privatechat.shared.model.DispatchEnd
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import com.rhodes.privatechat.viewmodel.shared.MemoryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "Dispatch"
private const val MAX_DISPATCH_LOG_CHARS = 20_000
private val json = Json { ignoreUnknownKeys = true }

private object Log {
    fun d(tag: String, message: String) = Unit
    fun i(tag: String, message: String) = Unit
    fun w(tag: String, message: String) = Unit
    fun e(tag: String, message: String) = Unit
}

class DispatchViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val sharedUtils: SharedUtils,
    private val operatorStateUpdater: OperatorStateUpdater,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val refreshAllOperatorStatus: suspend () -> Unit,
    private val getUserProfile: () -> UserProfile
) {
    /** 启动互斥锁（防止重复 startDispatch） */
    private val startingLock = java.util.concurrent.atomic.AtomicBoolean(false)
    val isStarting: Boolean get() = startingLock.get()
    private val generatingDispatchIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val finishingIds = ConcurrentHashMap<String, Boolean>()
    private val generatingSegments = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun compactLogChain(log: String): String {
        if (log.length <= MAX_DISPATCH_LOG_CHARS) return log
        val head = log.take(6_000).trimEnd()
        val tail = log.takeLast(MAX_DISPATCH_LOG_CHARS - head.length - 80).trimStart()
        return "$head\n\n【日志已压缩，省略中间过长内容】\n\n$tail"
    }

    // 段落辅助函数（按固定标记分割，避免AI正文中的\n\n干扰）
    private fun parseSegmentCount(logChain: String): Int {
        if (logChain.isBlank()) return 0
        val markers = listOf("【开局】", "【第", "【结局】", "【已中断】")
        var count = 0
        var pos = 0
        while (pos < logChain.length) {
            val found = markers.mapNotNull { m ->
                val idx = logChain.indexOf(m, pos)
                if (idx >= 0) idx to m else null
            }.minByOrNull { it.first }
            if (found == null) break
            count++
            pos = found.first + found.second.length
        }
        return count
    }

    private suspend fun generateDispatchProgressSuspend(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>, roundNum: Int, logSummary: String, logChain: String): Boolean {
        val ops = operatorIds.mapNotNull { repository.getOperator(it) }
        val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
        val budgetLevel = when { budget < 300 -> "低"; budget < 800 -> "中"; else -> "高" }
        repeat(3) { attempt ->
            try {
                val dMn = settings.dispatchMinChars; val dMx = settings.dispatchMaxChars
                val prompt = """
你是罗德岛的战术记录员，也是冒险小说作家。为进行中的派遣行动续写故事。

续写派遣冒险的第${roundNum}轮过程日志。

【派遣信息】
任务类型：${taskType}
预算等级：${budgetLevel}（低预算事件倾向危险和损失，高预算倾向顺利和意外收获）
前情提要：${logSummary.take(200)}

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx}字，第三人称叙事，承接前情，剧情连贯
- 鼓励出现一个具体事件（遭遇敌人、发现遗迹、天气突变等），也可延续上一轮的张力（追踪、等待、谈判中）
- 所有成员必须被提及，名字和人设必须与前文一致

【人称约束】
- 叙述文字使用第三人称，不从任何角色第一人称视角讲述
- 角色台词可以自然使用"我""你"，但叙述者不要使用"我""我们"代指小队

【格式约束】
- 只输出叙事文本，不要输出JSON、格式标记、Markdown

直接输出过程叙事。
""".trimIndent()
                DebugLogger.log("Dispatch/AI", "过程段请求: id=$dispatchId, 第${roundNum}轮, prompt长度=${prompt.length}")
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                val latest = repository.getDispatch(dispatchId) ?: return false
                if (latest.status != "active" || latest.endTime > 0) {
                    DebugLogger.log("Dispatch/AI", "过程段丢弃: id=$dispatchId, status=${latest.status}, endTime=${latest.endTime}")
                    return false
                }
                val newLog = compactLogChain(latest.logChain + "\n\n【第${roundNum}轮】" + rawResult)
                repository.insertDispatch(latest.copy(logChain = newLog, status = "active"))
                DebugLogger.log("Dispatch/AI", "过程段成功: id=$dispatchId, 第${roundNum}轮, ${rawResult.length}字")
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.log("Dispatch/AI", "过程段异常: id=$dispatchId, attempt=${attempt + 1}, ${e.message?.take(100)}")
                if (attempt < 2) delay(1000L * (attempt + 1))
            }
        }
        DebugLogger.log("Dispatch/AI", "过程段3次失败: id=$dispatchId")
        return false
    }

    fun startDispatch(id: String, task: String, duration: Int, budget: Int, operatorIds: List<String>, onSuccess: () -> Unit = {}) {
        if (!startingLock.compareAndSet(false, true)) { Log.w(TAG, "[startDispatch] 已在启动中，忽略重复调用 id=$id"); return }
        generatingDispatchIds.add(id)
        val segmentsPerHour = mapOf(1 to 5, 3 to 8, 5 to 12)
        val totalSeg = segmentsPerHour[duration] ?: 5
        val interval = (if (settings.dispatchFastMode) 30_000L
            else (duration.toLong() * 3_600_000 / totalSeg)).coerceAtLeast(30_000L)
        val dispatchStartTime = System.currentTimeMillis()
        Log.i(TAG, "[startDispatch] 启动派遣 id=$id task=$task duration=${duration}h budget=$budget segments=$totalSeg interval=${interval}ms ops=${operatorIds.size}")
        scope.launch {
            try {
                val activeDispatches = repository.getActiveDispatches()
                if (activeDispatches.size >= 2) {
                    Log.w(TAG, "[startDispatch] 已有两个小队在派遣，取消")
                    return@launch
                }
                val activeOperatorIds = activeDispatches.flatMap { it.operatorIds.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                if (operatorIds.any { it in activeOperatorIds }) {
                    Log.w(TAG, "[startDispatch] 存在已派遣干员，取消")
                    return@launch
                }

                // 1. 先插入 generating 记录（确保记录存在，防止扣费后崩溃无记录）
                repository.insertDispatch(DispatchRecord(
                    id = id, taskType = task, durationHours = duration,
                    budget = budget, netProfit = 0, operatorIds = operatorIds.joinToString(","),
                    logChain = "", status = "generating", startTime = dispatchStartTime,
                    totalSegments = totalSeg, segmentInterval = interval, items = "[]"
                ))
                Log.d(TAG, "[startDispatch] 已插入generating记录")

                // 2. 再扣预算（记录已存在，即使后续崩溃也可以通过记录退款）
                if (!settings.trySpendLmb(budget)) {
                    Log.w(TAG, "[startDispatch] 余额不足，取消并清除记录")
                    repository.getDispatch(id)?.let { d ->
                        repository.insertDispatch(d.copy(status = "cancelled", endTime = System.currentTimeMillis(), netProfit = 0))
                    }
                    return@launch
                }
                DebugLogger.log("Dispatch", "已扣除预算: id=$id, budget=$budget, 余额=${settings.lmb}")

                // 3. 导航到进度页
                Log.d(TAG, "[startDispatch] 导航到进度页")
                onSuccess()

                // 3. 更新干员状态
                for (opId in operatorIds) {
                    val op = repository.getOperator(opId) ?: continue
                    repository.updateOperator(op.copy(location = "外出", activity = task, emotion = "专注"))
                }
                operatorStateUpdater.notifyNearbyObservers(operatorIds)

                // 3. 生成开局段（轻量，20s 超时）
                val ops = operatorIds.mapNotNull { repository.getOperator(it) }
                val dMn = settings.dispatchMinChars; val dMx = settings.dispatchMaxChars
                val startOk = generateDispatchStartSuspend(id, task, budget, operatorIds, totalSeg, interval, dispatchStartTime)
                if (!startOk) {
                    Log.e(TAG, "[startDispatch] 开局段生成失败，退款")
                    settings.addLmb(budget)
                    val failed = repository.getDispatch(id)
                    if (failed != null) repository.insertDispatch(failed.copy(status = "cancelled", endTime = System.currentTimeMillis(), netProfit = 0))
                    refreshAllOperatorStatus()
                    return@launch
                }
                // 更新为 active 状态
                val currentRecord = repository.getDispatch(id)
                if (currentRecord != null) {
                    repository.insertDispatch(currentRecord.copy(status = "active"))
                }
                Log.i(TAG, "[startDispatch] 开局段生成成功，后续段落由后台生成")
            } finally {
                startingLock.set(false)
                generatingDispatchIds.remove(id)
            }
        }
    }

    fun finishDispatch(dispatchId: String) {
        if (finishingIds.putIfAbsent(dispatchId, true) != null) { Log.w(TAG, "[finishDispatch] 已在结算中 id=$dispatchId，跳过"); return }
        scope.launch {
            try {
                val d = repository.getDispatch(dispatchId) ?: run { Log.w(TAG, "[finishDispatch] 记录不存在 id=$dispatchId"); return@launch }
                if (d.status != "active") { Log.w(TAG, "[finishDispatch] 状态非active: ${d.status}，跳过"); return@launch }
                if (d.endTime > 0) { Log.w(TAG, "[finishDispatch] endTime已设置(${d.endTime})，跳过重复结算"); return@launch }
                Log.i(TAG, "[finishDispatch] 完成派遣 id=$dispatchId task=${d.taskType}")

                // 生成结局叙事（失败不影响结算）
                var netProfit = d.netProfit
                var reward = 0
                if (d.logChain.isNotBlank()) {
                    val result = generateDispatchEndSuspend(dispatchId, d.taskType, d.durationHours, d.budget, d.operatorIds.split(","))
                    netProfit = result.first; reward = result.second
                } else {
                    DebugLogger.log("Dispatch", "logChain为空，跳过结局生成: id=$dispatchId")
                }

                // 重新读取，结算（预算已在开始时扣除，这里只加 currency_reward）
                val updated = repository.getDispatch(dispatchId) ?: d
                if (updated.status != "active" || updated.endTime > 0) {
                    Log.w(TAG, "[finishDispatch] 结算前状态已变化: ${updated.status}/${updated.endTime}，跳过入账")
                    return@launch
                }
                repository.insertDispatch(updated.copy(status = "finished", endTime = System.currentTimeMillis(), netProfit = netProfit))
                Log.d(TAG, "[finishDispatch] 已更新为finished")
                refreshAllOperatorStatus()
                val addAmount = if (reward > 0) reward else netProfit + d.budget
                val newBalance = settings.addLmb(addAmount)
                DebugLogger.log("Dispatch", "入账: id=$dispatchId, add=$addAmount (reward=$reward netProfit=$netProfit), 余额=$newBalance")
                val profile = getUserProfile()
                val items = try {
                    val arr = Json.parseToJsonElement(updated.items) as? JsonArray
                    if (arr != null && arr.isNotEmpty()) arr.map { it.jsonPrimitive.content }.take(3).joinToString("、") else "无"
                } catch (_: Exception) { "未知" }
                val allOps = appState.operators.value
                val memberIds = updated.operatorIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val memberNames = memberIds.mapNotNull { id -> allOps.find { it.id == id || it.name == id }?.name }.take(3).joinToString("、")
                for (opId in memberIds) {
                    repository.saveAnchor(AnchorSourcePolicy.buildAnchor(source = AnchorSourcePolicy.DISPATCH, sourceName = updated.taskType, sourceActor = memberNames, sourceTarget = profile.nickname, operatorId = opId, type = AnchorType.EVENT, content = "${updated.taskType}任务完成，${memberNames}带回${items}，净收益${netProfit}龙门币", importance = AnchorSourcePolicy.MEDIUM, sessionId = "dispatch:${dispatchId}", createdAt = System.currentTimeMillis(), expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)))
                }
            } finally {
                finishingIds.remove(dispatchId)
            }
        }
    }

    private suspend fun generateDispatchEndSuspend(dispatchId: String, taskType: String, duration: Int, budget: Int, operatorIds: List<String>): Pair<Int, Int> {
        val ops = operatorIds.mapNotNull { repository.getOperator(it) }
        val names = ops.joinToString("、") { it.name }
        val memberCount = ops.size
        val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
        repeat(3) { attempt ->
            try {
                val dispatch = repository.getDispatch(dispatchId) ?: return Pair(0, 0)
                if (dispatch.status != "active" || dispatch.endTime > 0) return Pair(0, 0)
                val dMn = settings.dispatchMinChars; val dMx = settings.dispatchMaxChars
                val prompt = """
你是罗德岛的战术记录员，也是冒险小说作家。为即将结束的派遣行动撰写结局。

【派遣信息】
任务类型：${taskType}
耗时：${duration}小时
投入预算：${budget}龙门币
小队成员：${names}（共${memberCount}人，所有成员必须在结局中提及归队情况）

【完整日志摘要】
${dispatch.logChain.take(800)}

【最近过程片段】
${dispatch.logChain.takeLast(1500)}

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx + 50}字结局叙事
- 描写小队返回罗德岛的场景：疲惫、收获、伤病、意外发现
- 结局有情绪收束：疲惫后的欣慰、失落中的意外收获、一个被当宝贝捡回来的废品
- 描述本次任务获得的所有物品
- 必须承接“最近过程片段”里的关键事件，不要像另一个任务的结尾

【人称约束 - 必须遵守】
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发
- 叙述文字使用角色名字称呼角色，禁止用"我""我们"代指小队
- 角色台词可以自然使用"我""你"，但叙述者不要使用第一人称代指小队

【输出格式】
严格输出以下JSON对象：
{
  "ending_content": "结局叙事内容",
  "items": ["获得的物品1"],
  "currency_reward": 物品卖出后的龙门币总额,
  "net_profit": 净收益
}

【字段解释】
- ending_content：结局叙事文本
- items：字符串数组
- currency_reward：0~${budget * 10}之间的整数
- net_profit：必须等于 currency_reward - ${budget}
- items 写 1~6 个具体物品名，不要写长句

直接输出JSON对象。
""".trimIndent()
                DebugLogger.log("Dispatch/AI", "结局请求: id=$dispatchId")
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                val cleaned = sharedUtils.aiService.cleanJson(rawResult.trim())
                val ending = try { json.decodeFromString<DispatchEnd>(cleaned) } catch (_: Exception) {
                    DebugLogger.log("Dispatch/AI", "结局JSON解析失败: ${rawResult.take(100)}")
                    null
                }
                if (ending == null) {
                    throw IllegalStateException("派遣结局JSON解析失败")
                }
                val reward = ending.currency_reward.coerceIn(0, budget * 10)
                val netP = reward - budget
                val latest = repository.getDispatch(dispatchId) ?: return Pair(0, 0)
                if (latest.status != "active" || latest.endTime > 0) return Pair(0, 0)
                val newLog = compactLogChain(latest.logChain + "\n\n【结局】" + ending.ending_content)
                val itemsJson = if (ending.items.isNotEmpty()) json.encodeToString(ending.items) else "[]"
                repository.insertDispatch(latest.copy(logChain = newLog, netProfit = netP, items = itemsJson))
                Log.i(TAG, "[generateDispatchEndSuspend] 成功 reward=$reward netProfit=$netP items=${ending.items}")
                DebugLogger.log("Dispatch/AI", "结局成功: reward=$reward, items=${ending.items}")
                return Pair(netP, reward)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.log("Dispatch/AI", "结局异常: attempt=${attempt + 1}, ${e.message?.take(100)}")
                Log.w(TAG, "[generateDispatchEndSuspend] attempt=${attempt + 1} 异常: ${e.message}")
                if (attempt < 2) delay(1000L * (attempt + 1))
            }
        }
        Log.e(TAG, "[generateDispatchEndSuspend] 3次尝试全部失败")
        return Pair(0, 0)
    }

    fun cancelDispatch(dispatchId: String) {
        scope.launch {
            val d = repository.getDispatch(dispatchId) ?: run { Log.w(TAG, "[cancelDispatch] 记录不存在 id=$dispatchId"); return@launch }
            Log.i(TAG, "[cancelDispatch] 中断派遣 id=$dispatchId task=${d.taskType} status=${d.status}")
            // 如果AI尚未完成（generating状态），退还预算
            if (d.status == "generating" && d.logChain.isBlank()) {
                settings.addLmb(d.budget)
                Log.i(TAG, "[cancelDispatch] 生成未完成，退还预算 ${d.budget}")
            }
            repository.insertDispatch(d.copy(logChain = compactLogChain(if (d.logChain.isNotBlank()) "${d.logChain}\n\n【已中断】" else "【已中断】"), status = "cancelled", endTime = System.currentTimeMillis(), netProfit = 0))
            refreshAllOperatorStatus()
        }
    }

    suspend fun recoverDispatches() {
        delay(500)
        val actives = repository.getActiveDispatches()
        Log.i(TAG, "[recoverDispatches] 发现 ${actives.size} 个活跃派遣 generatingIds=${generatingDispatchIds.size}")
        for (d in actives) {
            Log.d(TAG, "[recoverDispatches] 检查 id=${d.id} status=${d.status} task=${d.taskType} logLen=${d.logChain.length}")
            val segments = parseSegmentCount(d.logChain)
            val elapsed = System.currentTimeMillis() - d.startTime
            val totalDuration = d.durationHours * 3_600_000L
            Log.d(TAG, "[recoverDispatches] id=${d.id} elapsed=${elapsed}ms total=${totalDuration}ms segments=$segments")

            // 情况 A：从未生成内容
            if (d.status == "generating" && d.logChain.isBlank()) {
                if (d.id in generatingDispatchIds) {
                    Log.i(TAG, "[recoverDispatches] id=${d.id} 跳过：startDispatch正在处理")
                    continue
                }
                Log.i(TAG, "[recoverDispatches] id=${d.id} 从未生成，尝试generateDispatchStart")
                generateDispatchStart(d.id, d.taskType, d.budget, d.operatorIds.split(","))
                continue
            }

            // 情况 B：已超时 → 直接结算
            if (elapsed >= totalDuration) {
                Log.i(TAG, "[recoverDispatches] id=${d.id} 已超时，执行finishDispatch")
                finishDispatch(d.id)
                continue
            }

            // 情况 C：需要补充段落
            if (d.status == "active" && d.logChain.isNotBlank()) {
                val expectedSegments = (elapsed / d.segmentInterval.coerceAtLeast(1L)).toInt().coerceIn(1, d.totalSegments)
                if (segments < expectedSegments) {
                    Log.i(TAG, "[recoverDispatches] id=${d.id} 当前${segments}段，期望${expectedSegments}段，补充生成")
                    for (i in (segments + 1)..expectedSegments) {
                        val currentRec = repository.getDispatch(d.id)
                        val currentLog = currentRec?.logChain ?: d.logChain
                        val summary = currentLog.take(100)
                        generateDispatchProgressSuspend(d.id, d.taskType, d.budget, d.operatorIds.split(","), i, summary, currentLog)
                    }
                }
            }
        }
    }

    suspend fun checkActiveDispatches() {
        val actives = repository.getActiveDispatches()
        if (actives.isEmpty()) return
        val now = System.currentTimeMillis()
        Log.d(TAG, "[checkActiveDispatches] 检查 ${actives.size} 个活跃派遣")
        for (d in actives) {
            if (d.status != "active" || d.logChain.isBlank()) continue
            val elapsed = now - d.startTime
            val totalDuration = d.durationHours * 3_600_000L
            val segments = parseSegmentCount(d.logChain)

            // 已超时 → 结算
            if (elapsed >= totalDuration) {
                Log.i(TAG, "[checkActiveDispatches] id=${d.id} 已超时，结算")
                finishDispatch(d.id)
                continue
            }

            // 需要补充下一段
            val expectedSegments = (elapsed / d.segmentInterval.coerceAtLeast(1L)).toInt().coerceIn(1, d.totalSegments)
            if (segments < expectedSegments && segments < d.totalSegments && segments >= 0) {
                val segKey = "${d.id}_${segments + 1}"
                if (segKey !in generatingSegments) {
                    generatingSegments.add(segKey)
                    Log.i(TAG, "[checkActiveDispatches] id=${d.id} 生成第${segments + 1}段")
                    scope.launch {
                        try { generateDispatchProgressSuspend(d.id, d.taskType, d.budget, d.operatorIds.split(","), segments + 1, d.logChain.take(100), d.logChain) }
                        finally { generatingSegments.remove(segKey) }
                    }
                }
            }
        }
    }

    fun generateDispatchStart(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>) {
        Log.i(TAG, "[generateDispatchStart] id=$dispatchId task=$taskType budget=$budget ops=${operatorIds.size}")
        scope.launch {
            val existingRecord = repository.getDispatch(dispatchId)
            val duration = existingRecord?.durationHours ?: 1
            val segmentsPerHour = mapOf(1 to 5, 3 to 8, 5 to 12)
            val totalSeg = segmentsPerHour[duration] ?: 5
            val interval = if (settings.dispatchFastMode) 30_000L
                else (duration.toLong() * 3_600_000 / totalSeg)
            val startTime = existingRecord?.startTime ?: System.currentTimeMillis()
            val ok = generateDispatchStartSuspend(dispatchId, taskType, budget, operatorIds, totalSeg, interval, startTime)
            if (!ok) {
                Log.e(TAG, "[generateDispatchStart] 生成失败，退款并标记cancelled")
                settings.addLmb(budget)
                DebugLogger.log("Dispatch", "recovery开局失败退款: id=$dispatchId, budget=$budget")
                repository.getDispatch(dispatchId)?.let { d ->
                    if (d.status == "generating") {
                        repository.updateDispatch(dispatchId, "\n\n【生成失败，已取消】", "cancelled", System.currentTimeMillis(), 0)
                        refreshAllOperatorStatus()
                    }
                }
            }
        }
    }

    private suspend fun generateDispatchStartSuspend(
        dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>,
        totalSeg: Int, interval: Long, startTime: Long
    ): Boolean {
        Log.d(TAG, "[generateDispatchStartSuspend] id=$dispatchId totalSeg=$totalSeg interval=${interval}ms")
        val ops = operatorIds.mapNotNull { repository.getOperator(it) }
        if (ops.isEmpty()) { Log.e(TAG, "[generateDispatchStartSuspend] 干员列表为空"); return false }
        val names = ops.joinToString("、") { it.name }
        val memberCount = ops.size
        val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
        repeat(3) { attempt ->
            try {
                val dMn = settings.dispatchMinChars; val dMx = settings.dispatchMaxChars
                val prompt = """
你是罗德岛的战术记录员，也是冒险小说作家。不是写任务报告，而是写生动的开局故事。

为以下派遣任务撰写开局简报。

【派遣信息】
任务类型：${taskType}
小队成员：${names}（共${memberCount}人，所有成员必须在故事中被提及并描写）
投入预算：${budget}龙门币

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx}字，第三人称叙事，小说级描写
- 描写出发前准备：采购装备、讨论策略、互相打趣
- 为每个成员确立"本集特征"
- 埋下悬念或意外发现：神秘信件、不明脚印、远处声响
- 所有成员必须出场，名字和人设必须与档案一致

【叙事质量】
- 不是任务汇报，而是小说叙事。有场景、有情绪、有细节
- 在细微动作和环境中透露角色关系和情感状态
- 制造"可看性"——让读者能想象出这个场景的画面

【人称约束 - 必须遵守】
- 全文使用角色名字称呼角色（如"阿米娅""能天使"），禁止使用"我""我的""我们""咱们"等第一人称代词
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发
- 角色台词可以自然使用"我""你"，但叙述者不要使用第一人称代指小队

直接输出开局叙事。
""".trimIndent()
                Log.d(TAG, "[generateDispatchStartSuspend] AI请求 attempt=${attempt + 1}/3")
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                Log.d(TAG, "[generateDispatchStartSuspend] AI返回 ${rawResult.length}字")
                repository.updateDispatchFull(dispatchId, compactLogChain(rawResult.trim()), "active", totalSegments = totalSeg, segmentInterval = interval)
                Log.i(TAG, "[generateDispatchStartSuspend] 成功，已写入logChain")
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[generateDispatchStartSuspend] attempt=${attempt + 1} 异常: ${e.message}")
                if (attempt < 2) delay(1000L * (attempt + 1))
            }
        }
        Log.e(TAG, "[generateDispatchStartSuspend] 3次尝试全部失败")
        return false
    }
}


