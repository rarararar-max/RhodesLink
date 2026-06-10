package com.rhodes.privatechat.viewmodel

import android.util.Log
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.DispatchRecord
import com.rhodes.privatechat.shared.model.DispatchResponse
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
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
    @Volatile
    var isStarting = false
        private set

    /** 正在生成中的派遣ID，用于避免 recoverDispatches 与 startDispatch 并发 */
    @Volatile
    private var generatingDispatchId: String? = null

    // 段落辅助函数
    private fun parseSegmentCount(logChain: String): Int {
        if (logChain.isBlank()) return 0
        return try {
            val arr = Json.parseToJsonElement(logChain) as? JsonArray
            if (arr != null) arr.size
            else logChain.split("\n\n").filter { it.isNotBlank() }.size
        } catch (_: Exception) {
            logChain.split("\n\n").filter { it.isNotBlank() }.size
        }
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
        if (isStarting) { Log.w(TAG, "[startDispatch] 已在启动中，忽略重复调用 id=$id"); return }
        isStarting = true
        generatingDispatchId = id
        val segmentsPerHour = mapOf(1 to 5, 2 to 6, 3 to 8)
        val totalSeg = segmentsPerHour[duration] ?: 5
        val interval = if (settings.dispatchFastMode) 30_000L
            else (duration.toLong() * 3_600_000 / totalSeg)
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
                isStarting = false
                generatingDispatchId = null
            }
        }
    }

    fun finishDispatch(dispatchId: String) {
        scope.launch {
            val d = repository.getDispatch(dispatchId) ?: run { Log.w(TAG, "[finishDispatch] 记录不存在 id=$dispatchId"); return@launch }
            if (d.status != "active") { Log.w(TAG, "[finishDispatch] 状态非active: ${d.status}，跳过"); return@launch }
            if (d.endTime > 0) { Log.w(TAG, "[finishDispatch] endTime已设置(${d.endTime})，跳过重复结算"); return@launch }
            Log.i(TAG, "[finishDispatch] 完成派遣 id=$dispatchId task=${d.taskType} netProfit=${d.netProfit}")
            // 先改状态为finished，再发奖励，防止进程中断时 recover 重复发奖
            repository.updateDispatch(dispatchId, d.logChain, "finished", System.currentTimeMillis(), d.netProfit)
            Log.d(TAG, "[finishDispatch] 已更新为finished")
            refreshAllOperatorStatus()
            val balance = settings.lmb
            settings.lmb = balance + d.netProfit
            Log.d(TAG, "[finishDispatch] 入账 ${d.netProfit}，余额 ${balance + d.netProfit}")
            val profile = getUserProfile()
            val items = try { val arr = Json.parseToJsonElement(d.items) as JsonArray; arr.map { it.jsonPrimitive.content }.take(3).joinToString("、") } catch (_: Exception) { "未知" }
            val allOps = appState.operators.value
            val memberIds = d.operatorIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val memberNames = memberIds.mapNotNull { id -> allOps.find { it.id == id || it.name == id }?.name }.take(3).joinToString("、")
            for (opId in memberIds) {
                repository.saveAnchor(MemoryAnchor(sessionId = "anchor_${System.currentTimeMillis()}", operatorId = opId, type = AnchorType.EVENT, content = "${d.taskType}任务完成，${memberNames}带回${items}，净收益${d.netProfit}龙门币", isPrivate = false, createdAt = System.currentTimeMillis(), expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L))
            }
        }
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

    private suspend fun generateDispatch(record: DispatchRecord): Boolean {
        Log.i(TAG, "[generateDispatch] 开始 id=${record.id} task=${record.taskType} budget=${record.budget} ops=${record.operatorIds}")
        val ops = record.operatorIds.split(",").mapNotNull { repository.getOperator(it) }
        if (ops.isEmpty()) { Log.e(TAG, "[generateDispatch] 干员列表为空"); return false }
        val names = ops.joinToString("、") { it.name }
        val memberCount = ops.size
        val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
        val dMn = settings.dispatchMinChars; val dMx = settings.dispatchMaxChars
        val budgetLevel = when { record.budget < 300 -> "低（≤300）"; record.budget < 800 -> "中（300~800）"; else -> "高（≥800）" }
        val segmentsPerHour = mapOf(1 to 5, 2 to 6, 3 to 8)
        val totalSeg = segmentsPerHour[record.durationHours] ?: 5
        val storyStructure = when (record.durationHours) {
                1 -> """
写出5段故事：1段准备阶段 + 3段过程 + 1段结局。
- 准备阶段（${dMn}~${dMx}字）：出发前准备，埋悬念
- 过程阶段（3段，每段${dMn}~${dMx}字）：每段一个具体事件
- 结局阶段（${dMn}~${dMx}字）：返回罗德岛，呼应悬念"""
                2 -> """
写出6段故事：1段准备阶段 + 4段过程 + 1段结局。
- 准备阶段（${dMn}~${dMx}字）
- 过程阶段（4段，每段${dMn}~${dMx}字）
- 结局阶段（${dMn}~${dMx}字）"""
                else -> """
写出8段故事：1段准备阶段 + 6段过程 + 1段结局。
- 准备阶段（${dMn}~${dMx}字）
- 过程阶段（6段，每段${dMn}~${dMx}字）
- 结局阶段（${dMn}~${dMx}字）"""
            }
        val startHour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).get(java.util.Calendar.HOUR_OF_DAY)
        val timeOfDay = sharedUtils.getTimeOfDay(startHour)
        val durationDesc = when (record.durationHours) { 1 -> "短时快速"; 2 -> "常规"; else -> "长时间深入" }
        val prompt = """
【角色】
你是罗德岛的战术记录员，也是一位冒险小说作家。你正在为一次干员派遣行动撰写完整的故事。

【派遣信息】
任务类型：${record.taskType}
出发时间：${timeOfDay}（这是一个${timeOfDay}出发的${durationDesc}任务）
预计耗时：${record.durationHours}小时
小队成员：${names}（共${memberCount}人）
投入预算：${record.budget}龙门币（${budgetLevel}）

【成员档案】
$profiles

【预算影响】
- 低预算（≤300）：事件倾向危险和损失，但也可能有意外惊喜
- 中预算（300~800）：平衡
- 高预算（≥800）：倾向顺利和意外收获

【故事结构】
${storyStructure}

【叙事质量】
- 小说叙事，有场景、有情绪、有细节。制造"可看性"
- 不要让角色在对话中直接提及职业标签

【输出格式 · 最高优先级】
严格输出以下JSON对象，不加任何额外文字：
{
  "segments": [
    {"type":"prep","content":"准备阶段叙事","operator_states":[{"name":"阿米娅","emotion":"专注"},...]},
    {"type":"progress","content":"过程叙事","operator_states":[{"name":"阿米娅","emotion":"警觉"},...]},
    {"type":"ending","content":"结局叙事","operator_states":[{"name":"阿米娅","emotion":"欣慰"},...]}
  ],
  "items":["物品1","物品2"],
  "currency_reward": 物品总价值,
  "net_profit": 净收益
}

【字段解释】
- segments：共${totalSeg}段。第1段type="prep"，中间type="progress"，最后type="ending"。每段必须附带operator_states，列出所有${memberCount}个干员的情绪（不超过5汉字）
- items：物资数组，无则空数组[]
- currency_reward：整数，范围0~${record.budget * 10}
- net_profit：整数，必须等于currency_reward - ${record.budget}

【验证】
1. 段数是否为${totalSeg}？ 2. 第1段type=prep/最后一段type=ending？ 3. 每段operator_states包含所有${memberCount}干员？ 4. currency_reward在0~${record.budget * 10}？

直接输出JSON对象。
""".trimIndent()
        repeat(3) { attempt ->
            try {
                // 竞态保护：重新检查记录状态，避免与 startDispatch 并发
                val current = repository.getDispatch(record.id)
                if (current == null || current.status != "generating" || current.logChain.isNotBlank()) {
                    Log.i(TAG, "[generateDispatch] 跳过: 记录已被处理 status=${current?.status} logLen=${current?.logChain?.length}")
                    return current?.status == "active"
                }
                Log.d(TAG, "[generateDispatch] AI请求 attempt=${attempt + 1}/3")
                val rawResult = withTimeout(90_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                Log.d(TAG, "[generateDispatch] AI返回 ${rawResult.length}字")
                val cleaned = sharedUtils.aiService.cleanJson(rawResult.trim())
                val resp = try { json.decodeFromString<DispatchResponse>(cleaned) } catch (e: Exception) {
                    Log.w(TAG, "[generateDispatch] JSON解析失败: ${e.message}, cleaned=${cleaned.take(200)}")
                    null
                }
                val segments = resp?.segments
                if (resp != null && segments != null && segments.size == totalSeg) {
                    val logJson = json.encodeToString(segments)
                    val itemsJson = json.encodeToString(resp.items ?: emptyList<String>())
                    val rawReward = (resp.currency_reward ?: 0).coerceIn(0, record.budget * 10)
                    val netP = rawReward - record.budget
                    Log.i(TAG, "[generateDispatch] 成功 segments=${segments.size} reward=$rawReward netProfit=$netP")
                    repository.insertDispatch(DispatchRecord(id = record.id, taskType = record.taskType, durationHours = record.durationHours, budget = record.budget, netProfit = netP, operatorIds = record.operatorIds, logChain = logJson, status = "active", startTime = System.currentTimeMillis(), totalSegments = totalSeg, segmentInterval = record.segmentInterval, items = itemsJson))
                    return true
                } else {
                    Log.w(TAG, "[generateDispatch] 验证失败: resp=${resp != null} segments=${segments?.size} 期望=$totalSeg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[generateDispatch] attempt=${attempt + 1} 异常: ${e.message}")
            }
            if (attempt < 2) delay(1000L * (attempt + 1))
        }
        Log.e(TAG, "[generateDispatch] 3次尝试全部失败 id=${record.id}")
        return false
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
                        val summary = d.logChain.take(100)
                        val ok = generateDispatchProgressSuspend(d.id, d.taskType, d.budget, d.operatorIds.split(","), i, summary, d.logChain)
                        if (!ok) {
                            Log.w(TAG, "[recoverDispatches] id=${d.id} 第${i}段生成失败")
                            break
                        }
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
                Log.i(TAG, "[checkActiveDispatches] id=${d.id} 生成第${segments + 1}段")
                scope.launch {
                    generateDispatchProgressSuspend(d.id, d.taskType, d.budget, d.operatorIds.split(","), segments + 1, d.logChain.take(100), d.logChain)
                }
            }
        }
    }

    fun generateDispatchStart(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>) {
        Log.i(TAG, "[generateDispatchStart] id=$dispatchId task=$taskType budget=$budget ops=${operatorIds.size}")
        scope.launch {
            val existingRecord = repository.getDispatch(dispatchId)
            val duration = existingRecord?.durationHours ?: 1
            val segmentsPerHour = mapOf(1 to 5, 2 to 6, 3 to 8)
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
                repository.updateDispatchFull(dispatchId, sharedUtils.aiService.cleanJson(rawResult.trim()), "active", totalSegments = totalSeg, segmentInterval = interval)
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

    fun generateDispatchProgress(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>, roundNum: Int, logSummary: String) {
        Log.i(TAG, "[generateDispatchProgress] id=$dispatchId round=$roundNum task=$taskType")
        scope.launch {
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            val budgetLevel = when { budget < 300 -> "低"; budget < 800 -> "中"; else -> "高" }
            repeat(3) { attempt ->
                try {
                    Log.d(TAG, "[generateDispatchProgress] AI请求 attempt=${attempt + 1}/3 round=$roundNum")
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
- 事件与成员性格和职业特性相关：狙击手可能先发现敌情，医疗干员可能照顾伤员
- 上次出场较少的成员本轮必须有戏份
- 所有成员必须被提及，名字和人设必须与前文一致，严禁替换或遗漏

【叙事质量】
- 不是任务汇报，而是小说叙事。有场景、有情绪、有细节
- 在细微动作和环境中透露角色关系和情感状态
- 对话推动剧情，旁白渲染氛围
- 制造"可看性"——让读者能想象出这个场景的画面

【人设表达要真实】
- 永远不要让角色在对话中直接提及自己的职业标签、特殊物品、习惯
- 角色的性格和爱好只能通过行为、语气、关注点来间接体现

【人称约束 - 必须遵守】
- 全文使用角色名字称呼角色，禁止使用"我""我的""我们""咱们"等第一人称代词
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发
- 角色之间的对话中用"对方""博士""队长"等第三人称称呼，不用"你""我"

直接输出过程叙事。
""".trimIndent()
                    val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                    sharedUtils.trackTokens("dispatch", prompt, rawResult)
                    Log.d(TAG, "[generateDispatchProgress] AI返回 ${rawResult.length}字 round=$roundNum")
                    val existing = repository.getDispatch(dispatchId)
                    val newLog = (existing?.logChain ?: "") + "\n\n【第${roundNum}轮】" + rawResult
                    repository.updateDispatch(dispatchId, newLog, "active")
                    Log.i(TAG, "[generateDispatchProgress] 成功 round=$roundNum logLen=${newLog.length}")
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "[generateDispatchProgress] attempt=${attempt + 1} round=$roundNum 异常: ${e.message}")
                    if (attempt < 2) delay(1000L * (attempt + 1))
                }
            }
            Log.e(TAG, "[generateDispatchProgress] 3次尝试全部失败 round=$roundNum")
        }
    }

    fun generateDispatchEnd(dispatchId: String, taskType: String, duration: Int, budget: Int, operatorIds: List<String>) {
        Log.i(TAG, "[generateDispatchEnd] id=$dispatchId task=$taskType duration=${duration}h budget=$budget")
        scope.launch {
            val dispatch = repository.getDispatch(dispatchId) ?: run { Log.w(TAG, "[generateDispatchEnd] 记录不存在"); return@launch }
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            repeat(3) { attempt ->
                try {
                    Log.d(TAG, "[generateDispatchEnd] AI请求 attempt=${attempt + 1}/3")
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

【叙事质量】
- 不是任务汇报，而是小说叙事。有场景、有情绪、有细节
- 结局应该有回响——呼应开局埋下的悬念

【人称约束 - 必须遵守】
- 全文使用角色名字称呼角色，禁止使用"我""我的""我们""咱们"等第一人称代词
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发

【输出格式 · 最高优先级】
严格输出以下JSON对象：
{
  "ending_content": "结局叙事内容",
  "items": ["获得的物品1", "获得的物品2"],
  "currency_reward": 物品卖出后的龙门币总额,
  "net_profit": 净收益
}

【输出字段解释】
- ending_content：结局叙事文本，${dMn}~${dMx}字
- items：字符串数组，列出所有获得的物资。如果一无所获，写空数组[]
- currency_reward：所有物品卖出后的龙门币总额，整数。必须是0~${budget * 10}之间的整数。items为空时必须为0
- net_profit：净收益，整数。必须等于 currency_reward - ${budget}。可以为负数（代表亏损）

【财务验证 · 需在输出前自行确认】
1. currency_reward 是否在 0 到 ${budget * 10} 之间？
2. net_profit 是否等于 currency_reward - ${budget}？
3. 如果 items 为空数组，currency_reward 是否为0？
如果以上任何一条不满足，请修正后再输出。

直接输出JSON对象。
""".trimIndent()
                    val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt))) }
                    sharedUtils.trackTokens("dispatch", prompt, rawResult)
                    Log.d(TAG, "[generateDispatchEnd] AI返回 ${rawResult.length}字")
                    val cleaned = sharedUtils.aiService.cleanJson(rawResult.trim())
                    val ending = try { json.decodeFromString<DispatchEnd>(cleaned) } catch (e: Exception) {
                        Log.w(TAG, "[generateDispatchEnd] JSON解析失败: ${e.message}, cleaned=${cleaned.take(200)}")
                        null
                    }
                    val rawReward = (ending?.currency_reward ?: 0).coerceIn(0, budget * 10)
                    val netProfit = rawReward - budget
                    Log.i(TAG, "[generateDispatchEnd] 成功 reward=$rawReward netProfit=$netProfit items=${ending?.items?.size ?: 0}")
                    repository.updateDispatch(dispatchId, dispatch.logChain + "\n\n【结局】${ending?.ending_content ?: rawResult}", "finished", System.currentTimeMillis(), netProfit)
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "[generateDispatchEnd] attempt=${attempt + 1} 异常: ${e.message}")
                    if (attempt < 2) delay(1000L * (attempt + 1))
                }
            }
            Log.e(TAG, "[generateDispatchEnd] 3次尝试全部失败")
        }
    }
}
