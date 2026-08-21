package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.shared.model.DispatchRecord
import com.rhodes.privatechat.shared.model.DispatchEnd
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.shared.UserProfile
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
    private val getUserProfile: () -> UserProfile,
) {
    /** 启动互斥锁（防止重复 startDispatch） */
    private val startingLock = java.util.concurrent.atomic.AtomicBoolean(false)
    val isStarting: Boolean get() = startingLock.get()
    private val generatingDispatchIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val finishingIds = ConcurrentHashMap<String, Boolean>()
    private val generatingSegments = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun dispatchPrompt(mode: String, values: Map<String, String>): String {
        val template = settings.resolvePromptTemplate("dispatch", mode, PromptTemplates.get("dispatch", mode), PromptTemplates.VERSION)
        return sharedUtils.applyTemplate(template, values)
    }

    private fun compactLogChain(log: String): String {
        if (log.length <= MAX_DISPATCH_LOG_CHARS) return log
        val head = log.take(6_000).trimEnd()
        val tail = log.takeLast(MAX_DISPATCH_LOG_CHARS - head.length - 80).trimStart()
        return "$head\n\n【日志已压缩，省略中间过长内容】\n\n$tail"
    }

    /** Refund a startup record only while it is still in the one refundable state. */
    private suspend fun cancelGeneratingWithRefund(dispatchId: String, reason: String) {
        val record = repository.getDispatch(dispatchId) ?: return
        if (record.status != "generating" || record.endTime > 0L) return
        settings.addLmb(record.budget)
        repository.insertDispatch(record.copy(
            logChain = reason,
            status = "cancelled",
            endTime = System.currentTimeMillis(),
            netProfit = 0
        ))
        refreshAllOperatorStatus()
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
                val recentPlot = logChain.takeLast(900).ifBlank { logSummary.takeLast(300) }
                val prompt = dispatchPrompt("progress", mapOf(
                    "TASK_TYPE" to taskType, "BUDGET_LEVEL" to budgetLevel,
                    "DISPATCH_ROUND" to roundNum.toString(), "DISPATCH_SUMMARY" to logSummary.take(200),
                    "RECENT_PLOT" to recentPlot, "MEMBER_PROFILES" to profiles,
                    "DISPATCH_MIN_CHARS" to dMn.toString(), "DISPATCH_MAX_CHARS" to dMx.toString()
                ))
                DebugLogger.log("Dispatch/AI", "过程段请求: id=$dispatchId, 第${roundNum}轮, prompt长度=${prompt.length}")
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                val latest = repository.getDispatch(dispatchId) ?: return false
                if (latest.status != "active" || latest.endTime > 0) {
                    DebugLogger.log("Dispatch/AI", "过程段丢弃: id=$dispatchId, status=${latest.status}, endTime=${latest.endTime}")
                    return false
                }
                val cleanResult = cleanGeneratedText(rawResult)
                if (cleanResult.isBlank()) return false
                val newLog = compactLogChain(latest.logChain + "\n\n【第${roundNum}轮】" + cleanResult)
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

    private fun cleanGeneratedText(raw: String): String = raw.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        .removePrefix("下面是").removePrefix("作为AI")

    fun startDispatch(id: String, task: String, duration: Int, budget: Int, operatorIds: List<String>, onSuccess: () -> Unit = {}) {
        if (!startingLock.compareAndSet(false, true)) { Log.w(TAG, "[startDispatch] 已在启动中，忽略重复调用 id=$id"); return }
        generatingDispatchIds.add(id)
        val segmentsPerHour = mapOf(1 to 5, 3 to 8, 5 to 12)
        val totalSeg = segmentsPerHour[duration] ?: 5
        val interval = (if (settings.dispatchFastMode) 30_000L
            else (duration.toLong() * 3_600_000 / totalSeg)).coerceAtLeast(30_000L)
        val dispatchStartTime = System.currentTimeMillis()
        var budgetSpent = false
        Log.i(TAG, "[startDispatch] 启动派遣 id=$id task=$task duration=${duration}h budget=$budget segments=$totalSeg interval=${interval}ms ops=${operatorIds.size}")
        scope.launch {
            try {
                if (task.isBlank() || duration !in setOf(1, 3, 5) || budget < 100 || operatorIds.size != 5 || operatorIds.toSet().size != 5) {
                    Log.w(TAG, "[startDispatch] 派遣参数无效 task=$task duration=$duration budget=$budget ops=${operatorIds.size}")
                    return@launch
                }
                // Validate before creating a record or charging the player. The UI check is
                // only a convenience; this method is also callable from other entry points.
                sharedUtils.chatConfigurationError()?.let {
                    Log.w(TAG, "[startDispatch] AI配置无效: $it")
                    return@launch
                }
                val activeDispatches = repository.getActiveDispatches()
                if (activeDispatches.size >= 2) {
                    Log.w(TAG, "[startDispatch] 已有两个小队在派遣，取消")
                    return@launch
                }
                if (repository.getDispatch(id) != null) {
                    Log.w(TAG, "[startDispatch] 派遣ID已存在，拒绝覆盖 id=$id")
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
                    repository.getDispatch(id)?.let { record ->
                        repository.insertDispatch(record.copy(
                            logChain = "【余额不足，已取消】",
                            status = "cancelled",
                            endTime = System.currentTimeMillis(),
                            netProfit = 0
                        ))
                    }
                    return@launch
                }
                budgetSpent = true
                DebugLogger.log("Dispatch", "已扣除预算: id=$id, budget=$budget, 余额=${settings.lmb}")

                // 3. 导航到进度页
                Log.d(TAG, "[startDispatch] 导航到进度页")
                onSuccess()

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[startDispatch] 启动异常: ${e.message}")
                // The budget is charged before the first AI request. Roll it back exactly
                // once when startup fails, and leave a visible terminal history record.
                if (budgetSpent) {
                    cancelGeneratingWithRefund(id, "【启动失败，预算已退还】")
                } else {
                    repository.getDispatch(id)?.let { record ->
                        if (record.status == "generating" && record.endTime <= 0L) {
                            repository.insertDispatch(record.copy(
                                logChain = "【启动失败，未扣除预算】",
                                status = "cancelled",
                                endTime = System.currentTimeMillis(),
                                netProfit = 0
                            ))
                        }
                    }
                }
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
                val totalDuration = d.durationHours.toLong() * 3_600_000L
                if (d.startTime <= 0L || d.durationHours <= 0 || d.totalSegments <= 0 || d.segmentInterval <= 0L ||
                    System.currentTimeMillis() - d.startTime < totalDuration) {
                    Log.w(TAG, "[finishDispatch] 派遣尚未到期或记录参数无效，跳过 id=$dispatchId")
                    return@launch
                }
                Log.i(TAG, "[finishDispatch] 完成派遣 id=$dispatchId task=${d.taskType}")

                // 生成结局叙事（失败不影响结算）
                var netProfit = d.netProfit
                var reward = 0
                if (d.logChain.isNotBlank()) {
                    // If the ending was persisted but the process died before the terminal
                    // status write, settle from the saved result instead of calling the AI again.
                    if (d.logChain.contains("【结局】")) {
                        netProfit = d.netProfit
                        reward = (d.netProfit + d.budget).coerceIn(0, d.budget * 10)
                    } else {
                        val result = generateDispatchEndSuspend(dispatchId, d.taskType, d.durationHours, d.budget, d.operatorIds.split(","))
                            ?: run {
                                Log.w(TAG, "[finishDispatch] 结局生成失败，保留active等待重试 id=$dispatchId")
                                return@launch
                            }
                        netProfit = result.first; reward = result.second
                    }
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
                // Dispatch history remains in its own domain.  It no longer creates a parallel
                // Anchor/vector memory; important outcomes can be promoted through the unified
                // event pipeline in a later dedicated product pass.
            } finally {
                finishingIds.remove(dispatchId)
            }
        }
    }

    private suspend fun generateDispatchEndSuspend(dispatchId: String, taskType: String, duration: Int, budget: Int, operatorIds: List<String>): Pair<Int, Int>? {
        val ops = operatorIds.mapNotNull { repository.getOperator(it) }
        val names = ops.joinToString("、") { it.name }
        val memberCount = ops.size
        val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
        repeat(3) { attempt ->
            try {
                val dispatch = repository.getDispatch(dispatchId) ?: return null
                if (dispatch.status != "active" || dispatch.endTime > 0) return null
                val dMn = settings.dispatchMinChars; val dMx = settings.dispatchMaxChars
                val prompt = dispatchPrompt("ending", mapOf(
                    "TASK_TYPE" to taskType, "DURATION_HOURS" to duration.toString(), "BUDGET" to budget.toString(),
                    "MEMBER_NAMES" to names, "MEMBER_COUNT" to memberCount.toString(),
                    "DISPATCH_SUMMARY" to dispatch.logChain.take(800), "RECENT_PLOT" to dispatch.logChain.takeLast(1500),
                    "MEMBER_PROFILES" to profiles, "DISPATCH_MIN_CHARS" to dMn.toString(),
                    "DISPATCH_ENDING_MAX_CHARS" to (dMx + 50).toString(), "MAX_CURRENCY_REWARD" to (budget * 10).toString()
                ))
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
                val latest = repository.getDispatch(dispatchId) ?: return null
                if (latest.status != "active" || latest.endTime > 0) return null
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
        return null
    }

    fun cancelDispatch(dispatchId: String) {
        scope.launch {
            val d = repository.getDispatch(dispatchId) ?: run { Log.w(TAG, "[cancelDispatch] 记录不存在 id=$dispatchId"); return@launch }
            if (d.status != "active" && d.status != "generating") {
                Log.w(TAG, "[cancelDispatch] 已是终态，跳过 id=$dispatchId status=${d.status}")
                return@launch
            }
            Log.i(TAG, "[cancelDispatch] 中断派遣 id=$dispatchId task=${d.taskType} status=${d.status}")
            // 如果AI尚未完成（generating状态），退还预算
            if (d.status == "generating" && d.logChain.isBlank()) {
                cancelGeneratingWithRefund(dispatchId, "【已中断，预算已退还】")
                return@launch
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

            if (d.startTime <= 0L || d.durationHours <= 0 || d.totalSegments <= 0 || d.segmentInterval <= 0L) {
                Log.w(TAG, "[recoverDispatches] 参数无效，取消并退款 id=${d.id}")
                if (d.status == "generating") {
                    cancelGeneratingWithRefund(d.id, "【派遣数据异常，预算已退还】")
                } else {
                    repository.insertDispatch(d.copy(
                        logChain = compactLogChain(if (d.logChain.isNotBlank()) "${d.logChain}\n\n【派遣数据异常，已取消】" else "【派遣数据异常，已取消】"),
                        status = "cancelled",
                        endTime = System.currentTimeMillis(),
                        netProfit = 0
                    ))
                    refreshAllOperatorStatus()
                }
                continue
            }

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
                val expectedSegments = (elapsed / d.segmentInterval).toInt().coerceIn(1, d.totalSegments)
                if (segments < expectedSegments) {
                    Log.i(TAG, "[recoverDispatches] id=${d.id} 当前${segments}段，期望${expectedSegments}段，补充生成")
                    for (i in (segments + 1)..expectedSegments) {
                        val segmentKey = "${d.id}_$i"
                        if (!generatingSegments.add(segmentKey)) continue
                        val currentRec = repository.getDispatch(d.id)
                        val currentLog = currentRec?.logChain ?: d.logChain
                        val summary = currentLog.take(100)
                        try {
                            generateDispatchProgressSuspend(d.id, d.taskType, d.budget, d.operatorIds.split(","), i, summary, currentLog)
                        } finally {
                            generatingSegments.remove(segmentKey)
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
            if (d.startTime <= 0L || d.durationHours <= 0 || d.totalSegments <= 0 || d.segmentInterval <= 0L) {
                Log.w(TAG, "[checkActiveDispatches] 跳过参数无效的派遣 id=${d.id}")
                continue
            }
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
            val expectedSegments = (elapsed / d.segmentInterval).toInt().coerceIn(1, d.totalSegments)
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
            if (settings.dispatchFastMode.not() && interval <= 0L) {
                Log.e(TAG, "[generateDispatchStart] 派遣时间参数无效 id=$dispatchId")
                cancelGeneratingWithRefund(dispatchId, "【派遣数据异常，预算已退还】")
                return@launch
            }
            val ok = generateDispatchStartSuspend(dispatchId, taskType, budget, operatorIds, totalSeg, interval, startTime)
            if (!ok) {
                Log.e(TAG, "[generateDispatchStart] 生成失败，退款并标记cancelled")
                DebugLogger.log("Dispatch", "recovery开局失败退款: id=$dispatchId, budget=$budget")
                cancelGeneratingWithRefund(dispatchId, "【生成失败，预算已退还】")
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
                val prompt = dispatchPrompt("start", mapOf(
                    "TASK_TYPE" to taskType, "MEMBER_NAMES" to names, "MEMBER_COUNT" to memberCount.toString(),
                    "BUDGET" to budget.toString(), "MEMBER_PROFILES" to profiles,
                    "DISPATCH_MIN_CHARS" to dMn.toString(), "DISPATCH_MAX_CHARS" to dMx.toString()
                ))
                Log.d(TAG, "[generateDispatchStartSuspend] AI请求 attempt=${attempt + 1}/3")
                val rawResult = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Dispatch") }
                sharedUtils.trackTokens("dispatch", prompt, rawResult)
                Log.d(TAG, "[generateDispatchStartSuspend] AI返回 ${rawResult.length}字")
                val latest = repository.getDispatch(dispatchId)
                if (latest == null || latest.status != "generating" || latest.endTime > 0L) {
                    Log.w(TAG, "[generateDispatchStartSuspend] 记录已取消或结束，丢弃AI结果 id=$dispatchId")
                    return false
                }
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


