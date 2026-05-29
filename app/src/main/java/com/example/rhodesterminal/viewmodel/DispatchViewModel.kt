package com.example.rhodesterminal.viewmodel

import com.example.rhodesterminal.shared.model.AnchorType
import com.example.rhodesterminal.shared.model.DispatchRecord
import com.example.rhodesterminal.shared.model.MemoryAnchor
import com.example.rhodesterminal.data.repository.ChatRepository
import com.example.rhodesterminal.shared.network.AIService
import com.example.rhodesterminal.shared.model.AiMessage
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.viewmodel.shared.OperatorStateUpdater
import com.example.rhodesterminal.viewmodel.shared.Prefs
import com.example.rhodesterminal.viewmodel.shared.SharedUtils
import com.example.rhodesterminal.viewmodel.shared.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class DispatchViewModel(
    private val repository: ChatRepository,
    private val prefs: Prefs,
    private val sharedUtils: SharedUtils,
    private val operatorStateUpdater: OperatorStateUpdater,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val refreshAllOperatorStatus: suspend () -> Unit,
    private val getUserProfile: () -> UserProfile
) {
    fun startDispatch(id: String, task: String, duration: Int, budget: Int, operatorIds: List<String>, onSuccess: () -> Unit = {}) {
        val segmentsPerHour = mapOf(1 to 5, 2 to 6, 3 to 8)
        val totalSeg = segmentsPerHour[duration] ?: 5
        val interval = if (prefs.intPref("dispatch_fast_mode", 0) == 1) 30_000L
            else (duration.toLong() * 3_600_000 / totalSeg)
        scope.launch {
            repository.insertDispatch(DispatchRecord(
                id = id, taskType = task, durationHours = duration,
                budget = budget, netProfit = 0, operatorIds = operatorIds.joinToString(","),
                logChain = "", status = "generating", startTime = System.currentTimeMillis(),
                totalSegments = totalSeg, segmentInterval = interval, items = "[]"
            ))
            onSuccess()
        }
        scope.launch {
            val dp = prefs.dispatch
            val balance = dp.getInt("lmb", 1000)
            if (balance < budget) { return@launch }
            dp.edit().putInt("lmb", balance - budget).apply()
            for (opId in operatorIds) {
                val op = repository.getOperator(opId) ?: continue
                repository.updateOperator(op.copy(location = "外出", activity = task, emotion = "专注"))
            }
            operatorStateUpdater.notifyNearbyObservers(operatorIds)
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            val dMn = prefs.intPref("dispatch_min_chars", 150); val dMx = prefs.intPref("dispatch_max_chars", 400)
            val budgetLevel = when { budget < 300 -> "低（≤300）"; budget < 800 -> "中（300~800）"; else -> "高（≥800）" }
            val storyStructure = when (duration) {
                1 -> "写出5段故事：1段准备阶段 + 3段过程 + 1段结局。\n- 准备阶段（${dMn}~${dMx}字）\n- 过程阶段（3段，每段${dMn}~${dMx}字）\n- 结局阶段（${dMn}~${dMx}字）"
                2 -> "写出6段故事：1段准备阶段 + 4段过程 + 1段结局。\n- 准备阶段（${dMn}~${dMx}字）\n- 过程阶段（4段，每段${dMn}~${dMx}字）\n- 结局阶段（${dMn}~${dMx}字）"
                else -> "写出8段故事：1段准备阶段 + 6段过程 + 1段结局。\n- 准备阶段（${dMn}~${dMx}字）\n- 过程阶段（6段，每段${dMn}~${dMx}字）\n- 结局阶段（${dMn}~${dMx}字）"
            }
            val startHour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).get(java.util.Calendar.HOUR_OF_DAY)
            val timeOfDay = sharedUtils.getTimeOfDay(startHour)
            val durationDesc = when (duration) { 1 -> "短时快速"; 2 -> "常规"; else -> "长时间深入" }
            val prompt = """【角色】你是罗德岛的战术记录员，也是一位冒险小说作家。你正在为一次干员派遣行动撰写完整的故事。

【派遣信息】任务类型：$task，出发时间：${timeOfDay}（${durationDesc}任务），预计耗时：${duration}小时，小队成员：$names（共${memberCount}人），投入预算：${budget}龙门币（${budgetLevel}）

【成员档案】$profiles

【预算影响】低预算（≤300）：事件倾向危险和损失；中预算（300~800）：平衡；高预算（≥800）：倾向顺利和意外收获

【故事结构】${storyStructure}

【输出格式 · 最高优先级】严格输出以下JSON对象：{"segments":[{"type":"prep","content":"准备阶段叙事","operator_states":[{"name":"阿米娅","emotion":"专注"}]},{"type":"progress","content":"过程叙事","operator_states":[]},{"type":"ending","content":"结局叙事","operator_states":[]}],"items":["物品1"],"currency_reward":0,"net_profit":0}

segments共${totalSeg}段，第1段type="prep"，中间type="progress"，最后type="ending"。每段必须附带operator_states。currency_reward范围0~${budget * 10}，net_profit必须等于currency_reward - $budget。直接输出JSON对象。""".trimIndent()
            try {
                val sb = StringBuilder()
                withTimeout(90_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt)), "Dispatch").collect { sb.append(it) } }
                sharedUtils.trackTokens("dispatch", prompt, sb.toString())
                val cleaned = sharedUtils.aiService.cleanJson(sb.toString().trim())
                val resp = try { com.google.gson.Gson().fromJson(cleaned, DispatchResponse::class.java) } catch (_: Exception) { null }
                if (resp != null && resp.segments != null && resp.segments.size == totalSeg) {
                    val logJson = com.google.gson.Gson().toJson(resp.segments)
                    val itemsJson = com.google.gson.Gson().toJson(resp.items ?: emptyList<String>())
                    val rawReward = (resp.currency_reward ?: 0).coerceIn(0, budget * 10)
                    val netP = rawReward - budget
                    repository.insertDispatch(DispatchRecord(id = id, taskType = task, durationHours = duration, budget = budget, netProfit = netP, operatorIds = operatorIds.joinToString(","), logChain = logJson, status = "active", startTime = System.currentTimeMillis(), totalSegments = totalSeg, segmentInterval = interval, items = itemsJson))
                } else {
                    repository.insertDispatch(DispatchRecord(id = id, taskType = task, durationHours = duration, budget = budget, operatorIds = operatorIds.joinToString(","), logChain = "", status = "cancelled", startTime = System.currentTimeMillis(), totalSegments = 0, segmentInterval = 0, items = "[]"))
                }
            } catch (e: Exception) {
                val cur = prefs.dispatch.getInt("lmb", 1000)
                prefs.dispatch.edit().putInt("lmb", cur + budget).apply()
                refreshAllOperatorStatus()
                repository.insertDispatch(DispatchRecord(id = id, taskType = task, durationHours = duration, budget = budget, operatorIds = operatorIds.joinToString(","), logChain = "", status = "cancelled", startTime = System.currentTimeMillis(), totalSegments = 0, segmentInterval = 0, items = "[]"))
            }
        }
    }

    fun finishDispatch(dispatchId: String) {
        scope.launch {
            val d = repository.getDispatch(dispatchId) ?: return@launch
            if (d.status != "active") return@launch
            refreshAllOperatorStatus()
            val dp = prefs.dispatch
            val balance = dp.getInt("lmb", 1000)
            dp.edit().putInt("lmb", balance + d.netProfit).apply()
            val profile = getUserProfile()
            val items = try { val arr = com.google.gson.JsonParser.parseString(d.items).asJsonArray; arr.map { it.asString }.take(3).joinToString("、") } catch (_: Exception) { "未知" }
            val allOps = appState.operators.value
            val memberIds = d.operatorIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val memberNames = memberIds.mapNotNull { id -> allOps.find { it.id == id || it.name == id }?.name }.take(3).joinToString("、")
            for (opId in memberIds) {
                repository.saveAnchor(MemoryAnchor(sessionId = "anchor_${System.currentTimeMillis()}", operatorId = opId, type = AnchorType.EVENT, content = "${d.taskType}任务完成，${memberNames}带回${items}，净收益${d.netProfit}龙门币", isPrivate = false))
            }
            repository.updateDispatch(dispatchId, d.logChain, "finished", System.currentTimeMillis(), d.netProfit)
        }
    }

    fun cancelDispatch(dispatchId: String) {
        scope.launch {
            val d = repository.getDispatch(dispatchId) ?: return@launch
            repository.updateDispatch(dispatchId, d.logChain + "\n\n【已中断】", "cancelled", System.currentTimeMillis(), 0)
            refreshAllOperatorStatus()
        }
    }

    suspend fun recoverDispatches() {
        val actives = repository.getActiveDispatches()
        for (d in actives) {
            if (d.status == "generating" && d.logChain.isBlank()) {
                generateDispatchStart(d.id, d.taskType, d.budget, d.operatorIds.split(","))
                continue
            }
            val elapsed = System.currentTimeMillis() - d.startTime
            val totalDuration = d.durationHours * 3_600_000L
            if (elapsed >= totalDuration) {
                finishDispatch(d.id)
            }
        }
    }

    fun generateDispatchStart(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>) {
        scope.launch {
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            repeat(3) { attempt ->
                try {
                    val dMn = prefs.intPref("dispatch_min_chars", 150); val dMx = prefs.intPref("dispatch_max_chars", 400)
                    val prompt = """你是罗德岛的战术记录员，也是冒险小说作家。为以下派遣任务撰写开局简报。

任务类型：${taskType}，小队成员：${names}（共${memberCount}人），投入预算：${budget}龙门币

成员档案：${profiles}

写作要求：${dMn}~${dMx}字，第三人称叙事，描写出发前准备，为每个成员确立特征，埋下悬念。所有成员必须出场。使用角色名字称呼，禁止第一人称。直接输出开局叙事。""".trimIndent()
                    val sb = StringBuilder()
                    withTimeout(20_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt)), "Dispatch").collect { sb.append(it) } }
                    sharedUtils.trackTokens("dispatch", prompt, sb.toString())
                    repository.updateDispatch(dispatchId, sharedUtils.aiService.cleanJson(sb.toString().trim()), "active")
                    return@launch
                } catch (_: Exception) { if (attempt < 2) delay(1000L * (attempt + 1)) }
            }
            repository.getDispatch(dispatchId)?.let { d ->
                if (d.status == "generating") {
                    repository.updateDispatch(dispatchId, "\n\n【生成失败，已取消】", "cancelled", System.currentTimeMillis(), 0)
                    refreshAllOperatorStatus()
                }
            }
        }
    }

    fun generateDispatchProgress(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>, roundNum: Int, logSummary: String) {
        scope.launch {
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            val budgetLevel = when { budget < 300 -> "低"; budget < 800 -> "中"; else -> "高" }
            repeat(3) { attempt ->
                try {
                    val dMn = prefs.intPref("dispatch_min_chars", 150); val dMx = prefs.intPref("dispatch_max_chars", 400)
                    val prompt = """你是罗德岛的战术记录员，也是冒险小说作家。续写派遣冒险的第${roundNum}轮过程日志。

任务类型：${taskType}，预算等级：${budgetLevel}，前情提要：${logSummary.take(100)}

成员档案：${profiles}

写作要求：${dMn}~${dMx}字，第三人称叙事，承接前情。本轮必须出现一个具体事件。所有成员必须被提及。使用角色名字称呼，禁止第一人称。直接输出过程叙事。""".trimIndent()
                    val sb = StringBuilder()
                    withTimeout(20_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt)), "Dispatch").collect { sb.append(it) } }
                    sharedUtils.trackTokens("dispatch", prompt, sb.toString())
                    val existing = repository.getDispatch(dispatchId)
                    val newLog = (existing?.logChain ?: "") + "\n\n【第${roundNum}轮】" + sb.toString()
                    repository.updateDispatch(dispatchId, newLog, "active")
                    return@launch
                } catch (_: Exception) { if (attempt < 2) delay(1000L * (attempt + 1)) }
            }
        }
    }

    fun generateDispatchEnd(dispatchId: String, taskType: String, duration: Int, budget: Int, operatorIds: List<String>) {
        scope.launch {
            val dispatch = repository.getDispatch(dispatchId) ?: return@launch
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            repeat(3) { attempt ->
                try {
                    val dMn = prefs.intPref("dispatch_min_chars", 150); val dMx = prefs.intPref("dispatch_max_chars", 400)
                    val prompt = """你是罗德岛的战术记录员，也是冒险小说作家。为即将结束的派遣行动撰写结局。

任务类型：${taskType}，耗时：${duration}小时，投入预算：${budget}龙门币，小队成员：${names}（共${memberCount}人）

完整日志摘要：${dispatch.logChain.take(200)}

成员档案：${profiles}

写作要求：${dMn}~${dMx}字结局叙事，描写小队返回罗德岛的场景，描述获得的物品。

输出格式：严格输出JSON对象：{"ending_content":"结局叙事","items":["物品1"],"currency_reward":0,"net_profit":0}

currency_reward范围0~${budget * 10}，net_profit必须等于currency_reward - ${budget}。直接输出JSON对象。""".trimIndent()
                    val sb = StringBuilder()
                    withTimeout(20_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt))).collect { sb.append(it) } }
                    sharedUtils.trackTokens("dispatch", prompt, sb.toString())
                    val cleaned = sharedUtils.aiService.cleanJson(sb.toString().trim())
                    val ending = try { com.google.gson.Gson().fromJson(cleaned, DispatchEnd::class.java) } catch (_: Exception) { null }
                    val rawReward = (ending?.currency_reward ?: 0).coerceIn(0, budget * 10)
                    val netProfit = rawReward - budget
                    repository.updateDispatch(dispatchId, dispatch.logChain + "\n\n【结局】${ending?.ending_content ?: sb.toString()}", "finished", System.currentTimeMillis(), netProfit)
                    return@launch
                } catch (_: Exception) { if (attempt < 2) delay(1000L * (attempt + 1)) }
            }
        }
    }
}
