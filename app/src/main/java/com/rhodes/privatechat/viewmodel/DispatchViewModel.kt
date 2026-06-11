package com.rhodes.privatechat.viewmodel

import android.util.Log
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.DispatchRecord
import com.rhodes.privatechat.shared.model.DispatchEnd
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "Dispatch"
private val json = Json { ignoreUnknownKeys = true }

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
    private var generatingDispatchId: String? = null
    private val finishingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val generatingSegments = mutableSetOf<String>()

    // 段落辅助函数
    private fun parseSegmentCount(logChain: String): Int {
        if (logChain.isBlank()) return 0
        return logChain.split("\n\n").filter { it.isNotBlank() }.size
    }

    private suspend fun generateDispatchProgressSuspend(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>, roundNum: Int, logSummary: String, logChain: String): Boolean {
        val ops = operatorIds.mapNotNull { repository.getOperator(it) }
        val names = ops.joinToString("、") { it.name }
        val memberCount = ops.size
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
前情提要：${logSummary.take(100)}

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx}字，第三人称叙事，承接前情，剧情连贯
- 本轮必须出现一个具体事件：遭遇敌人、发现遗迹、天气突变、物资丢失、队员争执等
- 所有成员必须被提及，名字和人设必须与前文一致

【人称约束】
- 全文使用角色名字称呼角色，禁止使用"我""我的""我们""咱们"
- 角色之间的对话中用"对方""博士""队长"等第三人称称呼

直接输出过程叙事。
""".trimIndent()
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                val newLog = logChain + "\n\n【第${roundNum}轮】" + rawResult
                repository.updateDispatch(dispatchId, newLog, "active")
                return true
            } catch (e: Exception) {
                if (attempt < 2) delay(1000L * (attempt + 1))
            }
        }
        return false
    }

    fun startDispatch(id: String, task: String, duration: Int, budget: Int, operatorIds: List<String>, onSuccess: () -> Unit = {}) {
        if (!startingLock.compareAndSet(false, true)) { Log.w(TAG, "[startDispatch] 已在启动中，忽略重复调用 id=$id"); return }
        generatingDispatchId = id
        val segmentsPerHour = mapOf(1 to 5, 3 to 8, 5 to 12)
        val totalSeg = segmentsPerHour[duration] ?: 5
        val interval = (if (settings.dispatchFastMode) 30_000L
            else (duration.toLong() * 3_600_000 / totalSeg)).coerceAtLeast(30_000L)
        val dispatchStartTime = System.currentTimeMillis()
        Log.i(TAG, "[startDispatch] 启动派遣 id=$id task=$task duration=${duration}h budget=$budget segments=$totalSeg interval=${interval}ms ops=${operatorIds.size}")
        scope.launch {
            try {
                // 1. 插入 generating 记录，立即导航
                repository.insertDispatch(DispatchRecord(
                    id = id, taskType = task, durationHours = duration,
                    budget = budget, netProfit = 0, operatorIds = operatorIds.joinToString(","),
                    logChain = "", status = "generating", startTime = dispatchStartTime,
                    totalSegments = totalSeg, segmentInterval = interval, items = "[]"
                ))
                Log.d(TAG, "[startDispatch] 已插入generating记录，导航到进度页")
                onSuccess()

                // 2. 扣除预算、更新干员状态
                val balance = settings.lmb
                if (balance < budget) {
                    Log.w(TAG, "[startDispatch] 余额不足，取消")
                    repository.updateDispatch(id, "", "cancelled", System.currentTimeMillis(), 0)
                    return@launch
                }
                settings.lmb = balance - budget
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
                    settings.lmb = settings.lmb + budget
                    repository.updateDispatch(id, "", "cancelled", System.currentTimeMillis(), 0)
                    refreshAllOperatorStatus()
                    return@launch
                }
                // 更新为 active 状态
                val currentRecord = repository.getDispatch(id)
                if (currentRecord != null) {
                    repository.updateDispatch(id, currentRecord.logChain, "active")
                }
                Log.i(TAG, "[startDispatch] 开局段生成成功，后续段落由后台生成")
            } finally {
                startingLock.set(false)
                generatingDispatchId = null
            }
        }
    }

    fun finishDispatch(dispatchId: String) {
        if (dispatchId in finishingIds) { Log.w(TAG, "[finishDispatch] 已在结算中 id=$dispatchId，跳过"); return }
        finishingIds.add(dispatchId)
        scope.launch {
            try {
                val d = repository.getDispatch(dispatchId) ?: run { Log.w(TAG, "[finishDispatch] 记录不存在 id=$dispatchId"); return@launch }
                if (d.status != "active") { Log.w(TAG, "[finishDispatch] 状态非active: ${d.status}，跳过"); return@launch }
                if (d.endTime > 0) { Log.w(TAG, "[finishDispatch] endTime已设置(${d.endTime})，跳过重复结算"); return@launch }
                Log.i(TAG, "[finishDispatch] 完成派遣 id=$dispatchId task=${d.taskType}")

                // 生成结局叙事（失败不影响结算）
                var netProfit = d.netProfit
                if (d.logChain.isNotBlank()) {
                    val endNet = generateDispatchEndSuspend(dispatchId, d.taskType, d.durationHours, d.budget, d.operatorIds.split(","))
                    if (endNet != 0) netProfit = endNet
                }

                // 重新读取，结算
                val updated = repository.getDispatch(dispatchId) ?: d
                repository.updateDispatch(dispatchId, updated.logChain, "finished", System.currentTimeMillis(), netProfit)
                Log.d(TAG, "[finishDispatch] 已更新为finished")
                refreshAllOperatorStatus()
                val balance = settings.lmb
                settings.lmb = balance + netProfit
                Log.d(TAG, "[finishDispatch] 入账 $netProfit，余额 ${balance + netProfit}")
                val profile = getUserProfile()
                val items = try { val arr = Json.parseToJsonElement(updated.items) as JsonArray; arr.map { it.jsonPrimitive.content }.take(3).joinToString("、") } catch (_: Exception) { "未知" }
                val allOps = appState.operators.value
                val memberIds = updated.operatorIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val memberNames = memberIds.mapNotNull { id -> allOps.find { it.id == id || it.name == id }?.name }.take(3).joinToString("、")
                for (opId in memberIds) {
                    repository.saveAnchor(MemoryAnchor(sessionId = "anchor_${System.currentTimeMillis()}", operatorId = opId, type = AnchorType.EVENT, content = "${updated.taskType}任务完成，${memberNames}带回${items}，净收益${netProfit}龙门币", isPrivate = false, createdAt = System.currentTimeMillis(), expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L))
                }
            } finally {
                finishingIds.remove(dispatchId)
            }
        }
    }

    private suspend fun generateDispatchEndSuspend(dispatchId: String, taskType: String, duration: Int, budget: Int, operatorIds: List<String>): Int {
        val ops = operatorIds.mapNotNull { repository.getOperator(it) }
        val names = ops.joinToString("、") { it.name }
        val memberCount = ops.size
        val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
        val dispatch = repository.getDispatch(dispatchId) ?: return 0
        repeat(3) { attempt ->
            try {
                val dMn = settings.dispatchMinChars; val dMx = settings.dispatchMaxChars
                val prompt = """
你是罗德岛的战术记录员，也是冒险小说作家。为即将结束的派遣行动撰写结局。

【派遣信息】
任务类型：${taskType}
耗时：${duration}小时
投入预算：${budget}龙门币
小队成员：${names}（共${memberCount}人，所有成员必须在结局中提及归队情况）

【完整日志摘要】
${dispatch.logChain.take(200)}

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx}字结局叙事
- 描写小队返回罗德岛的场景：疲惫、收获、伤病、意外发现
- 结局有情绪收束：疲惫后的欣慰、失落中的意外收获、一个被当宝贝捡回来的废品
- 描述本次任务获得的所有物品

【人称约束 - 必须遵守】
- 全文使用角色名字称呼角色，禁止使用"我""我的""我们""咱们"等第一人称代词
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发

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

直接输出JSON对象。
""".trimIndent()
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                val cleaned = sharedUtils.aiService.cleanJson(rawResult.trim())
                val ending = try { json.decodeFromString<DispatchEnd>(cleaned) } catch (_: Exception) { null }
                val reward = (ending?.currency_reward ?: 0).coerceIn(0, budget * 10)
                val netP = reward - budget
                val newLog = dispatch.logChain + "\n\n【结局】" + (ending?.ending_content ?: rawResult.take(500))
                repository.updateDispatch(dispatchId, newLog, "active")
                Log.i(TAG, "[generateDispatchEndSuspend] 成功 reward=$reward netProfit=$netP")
                return netP
            } catch (e: Exception) {
                Log.w(TAG, "[generateDispatchEndSuspend] attempt=${attempt + 1} 异常: ${e.message}")
                if (attempt < 2) delay(1000L * (attempt + 1))
            }
        }
        Log.e(TAG, "[generateDispatchEndSuspend] 3次尝试全部失败")
        return 0
    }

    fun cancelDispatch(dispatchId: String) {
        scope.launch {
            val d = repository.getDispatch(dispatchId) ?: run { Log.w(TAG, "[cancelDispatch] 记录不存在 id=$dispatchId"); return@launch }
            Log.i(TAG, "[cancelDispatch] 中断派遣 id=$dispatchId task=${d.taskType} status=${d.status}")
            // 如果AI尚未完成（generating状态），退还预算
            if (d.status == "generating" && d.logChain.isBlank()) {
                val cur = settings.lmb
                settings.lmb = cur + d.budget
                Log.i(TAG, "[cancelDispatch] 生成未完成，退还预算 ${d.budget}")
            }
            repository.updateDispatch(dispatchId, if (d.logChain.isNotBlank()) "${d.logChain}\n\n【已中断】" else "【已中断】", "cancelled", System.currentTimeMillis(), 0)
            refreshAllOperatorStatus()
        }
    }

    suspend fun recoverDispatches() {
        delay(500)
        val actives = repository.getActiveDispatches()
        Log.i(TAG, "[recoverDispatches] 发现 ${actives.size} 个活跃派遣 generatingId=$generatingDispatchId")
        for (d in actives) {
            Log.d(TAG, "[recoverDispatches] 检查 id=${d.id} status=${d.status} task=${d.taskType} logLen=${d.logChain.length}")
            val segments = parseSegmentCount(d.logChain)
            val elapsed = System.currentTimeMillis() - d.startTime
            val totalDuration = d.durationHours * 3_600_000L
            Log.d(TAG, "[recoverDispatches] id=${d.id} elapsed=${elapsed}ms total=${totalDuration}ms segments=$segments")

            // 情况 A：从未生成内容
            if (d.status == "generating" && d.logChain.isBlank()) {
                if (d.id == generatingDispatchId) {
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
                Log.e(TAG, "[generateDispatchStart] 生成失败，标记cancelled")
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
- 角色之间的对话中用"对方""博士""队长"等第三人称称呼，不用"你""我"

直接输出开局叙事。
""".trimIndent()
                Log.d(TAG, "[generateDispatchStartSuspend] AI请求 attempt=${attempt + 1}/3")
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                Log.d(TAG, "[generateDispatchStartSuspend] AI返回 ${rawResult.length}字")
                repository.updateDispatchFull(dispatchId, rawResult.trim(), "active", totalSegments = totalSeg, segmentInterval = interval)
                Log.i(TAG, "[generateDispatchStartSuspend] 成功，已写入logChain")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "[generateDispatchStartSuspend] attempt=${attempt + 1} 异常: ${e.message}")
                if (attempt < 2) delay(1000L * (attempt + 1))
            }
        }
        Log.e(TAG, "[generateDispatchStartSuspend] 3次尝试全部失败")
        return false
    }
}


