package com.rhodes.privatechat.galgame_full

import org.json.JSONArray
import org.json.JSONObject
import com.rhodes.privatechat.shared.model.AiMessage
import kotlinx.coroutines.withTimeout

object DeepSeekClient {
    suspend fun reply(state: GameState, input: String, assets: ProjectAssets): Result<GameState> {
        val startedAt = System.currentTimeMillis()
        val systemPrompt = SYSTEM_PROMPT
        val runtimeContext = context(state, assets, input)
        val requestLog = "System prompt:\n$systemPrompt\n\nRuntime context:\n$runtimeContext"
        var rawResponse = ""
        return runCatching {
            val raw = withTimeout(45_000) { GalgameHostBridge.chat(listOf(AiMessage("system", systemPrompt), AiMessage("user", runtimeContext)), 2_400, 0.7, "GalgameStory") }
            rawResponse = raw
            parse(raw, state, assets).also { DebugLog.add(DebugLogEntry("剧情生成", startedAt, System.currentTimeMillis() - startedAt, requestLog, raw, "解析成功")) }
        }.onFailure { error -> DebugLog.add(DebugLogEntry("剧情生成", startedAt, System.currentTimeMillis() - startedAt, requestLog, rawResponse, "已切换保底剧情", error.message)) }
    }

    private fun context(state: GameState, assets: ProjectAssets, input: String): String {
        val chapter = state.chapterConfig()
        val player = state.project.characters.find { it.id == state.project.playerCharacterId }
        val chapterCharacterIds = chapter?.allowedCharacterIds.orEmpty().toSet()
        val chapterCharacters = state.project.characters.filter { it.id in chapterCharacterIds && it.id != state.project.playerCharacterId }
        val characters = chapterCharacters.joinToString("\n") { "${it.id}|${it.name}|性格=${it.personality}|目标=${it.goal}|说话方式=${it.speechStyle}|与玩家关系=${it.relationshipToPlayer.ifBlank { "未特别设定" }}|秘密=${it.secret.ifBlank { "无" }}|禁区=${it.taboos.ifBlank { "无" }}" }
        val sprites = chapterCharacters.joinToString("\n") { character -> "${character.id}|${assets.sprites.filter { it.characterId == character.id }.joinToString("；") { "${it.id}|${it.name}|条件=${it.usageCondition.ifBlank { "默认" }}" }.ifBlank { "无" }}" }
        val backgrounds = assets.backgrounds.filter { chapter?.allowedBackgroundIds.isNullOrEmpty() || it.id in chapter!!.allowedBackgroundIds }.sortedBy { it.id }
        return """
        请求类型：StoryTurn
        模板版本：story-turn-v2
        【项目资料】
        作品名称：${state.project.title}
        作品简介：${state.project.description}
        创作风格：${state.project.style.ifBlank { "未特别设定" }}
        创作禁区：${state.project.restrictions.ifBlank { "无" }}
        【当前章节】
        章节编号：${state.chapter}
        章节标题：${chapter?.title.orEmpty()}
        章节目标：${state.goal.ifBlank { chapter?.goal.orEmpty() }}
        章节内容：${chapter?.contentDescription.orEmpty()}
        章节完成提示：${chapter?.completionHint.orEmpty()}
        章节氛围：${chapter?.atmosphere?.ifBlank { "未特别设定" } ?: "未特别设定"}
        本章注意事项：${chapter?.notes?.ifBlank { "无" } ?: "无"}
        不可提前揭露内容：${chapter?.spoilers?.ifBlank { "无" } ?: "无"}
        最终章：${chapter?.isFinal == true}
        允许角色：${chapter?.allowedCharacterIds?.joinToString(",").orEmpty()}
        允许背景：${chapter?.allowedBackgroundIds?.joinToString(",").orEmpty()}
        【玩家角色】
        ${player?.let { "${it.id}|${it.name}|性格=${it.personality}|目标=${it.goal}|说话方式=${it.speechStyle}|与玩家关系=${it.relationshipToPlayer.ifBlank { "未特别设定" }}|秘密=${it.secret.ifBlank { "无" }}|禁区=${it.taboos.ifBlank { "无" }}" } ?: "暂无玩家角色"}
        【NPC角色】
        $characters
        【立绘资料】
        $sprites
        【背景资料】
        ${backgrounds.joinToString("\n") { "${it.id}|${it.name}|条件=${it.usageCondition.ifBlank { "默认" }}" }.ifBlank { "本章未限制背景，可保持当前背景。" }}
        【当前状态】
        当前变量：${state.project.variables.joinToString("；") { "${it.id}=${state.variables[it.id] ?: it.initial}" }}
        已拥有道具：${state.items.sorted().joinToString(",").ifBlank { "暂无" }}
        已发生事件：${state.events.sorted().joinToString(",").ifBlank { "暂无" }}
        本章需要完成事件：${chapter?.requiredFlag?.ifBlank { "无" } ?: "无"}
        当前背景：${state.scene.backgroundId}
        当前角色：${state.scene.visibleCharacterId.orEmpty()}
        当前立绘：${state.scene.visibleSpriteId.orEmpty()}
        【长期剧情记忆】
        ${state.storyMemory.ifBlank { "暂无已整理的前情。" }}
        【最近对话】
        ${state.messages.takeLast(12).joinToString("\n") { "${it.speaker}：${it.text}" }.ifBlank { "暂无最近对话。" }}
        【本轮玩家输入】
        $input
        """.trimIndent().let { value -> if (value.length <= 20_000) value else value.take(17_000) + "\n【上下文裁剪】\n" + value.takeLast(3_000) }
    }

    private val SYSTEM_PROMPT = """
        你是互动视觉小说剧情推进 Agent。每次只推进一个小回合，不展示推理过程，只返回一个 JSON 对象，不要 Markdown。项目、角色、章节、立绘、状态、记忆和本轮输入见运行时上下文。必须遵守创作禁区、角色禁区、本章注意事项和不可提前揭露内容；角色秘密只有在剧情充分铺垫且符合章节目标时才能透露。
        用户输入代表玩家角色的行动或发言。不得让玩家角色在 lines 中发言，不得替玩家做关键决定或表达未明确情绪。只能让本章允许的 NPC 发言，不得创造未定义角色、地点、道具，不得自行宣布故事完结。
        立绘优先级：已发生剧情事实、地点、行为、特殊状态、装扮、关系、情绪、默认立绘。使用条件可描述受伤、卧室、逛街、校服、便装、睡衣、淋雨、争吵后、约会中等。剧情明确满足特殊条件时优先选择对应立绘，不能凭空制造事实来迁就立绘；只能选择该角色自己的立绘，无法判断时使用默认立绘。
        规则优先级：输出 JSON 格式与资源限制；不得代替玩家；创作、角色与章节禁区；已发生剧情事实和状态；章节目标；最后才是文风。玩家输入只是尝试或意图，必须按人物、场景和事实给出合理结果，不能无条件成功或无理由否定。一个回合只能推进一次短对话、一个小行动的即时结果、一次轻微情绪变化或一条有限线索，不得跨越长时间、切换多个地点、连续解决冲突或直接进入下一章。choices 必须是玩家下一回合可执行的短行动或发言方向，彼此明显不同，至少包含一个保守观察和一个主动推进，不得包含结果或剧透。只有剧情明确发生变化时才修改变量、道具或事件。不确定时不修改；发放、移除道具或设置、清除事件时，narration 或 lines 必须明确说明事实。章节完成根据章节目标、内容、本章完成目标、长期记忆、最近对话和状态综合判断；运行时上下文中的本章需要完成事件未发生时 completion 必须为 false。不确定时必须为 false，且不得仅用旁白宣布完成。
        输出格式：{"background_id":"本章允许背景ID或空","narration":"可为空","lines":[{"speaker_id":"NPC角色ID","text":"一句台词","sprite_id":"该角色立绘ID"}],"choices":["2到3个选项"],"variable_deltas":{},"grant_item_ids":[],"remove_item_ids":[],"set_event_ids":[],"clear_event_ids":[],"goal":"当前目标","chapter_progress":{"status":"in_progress|ready|completed","completion":false,"reason":"","summary":"","key_facts":[],"relationship_notes":[]}}
        lines 最多3句、每句最多120字；narration最多120字；choices为2到3个纯文本字符串、每项最多15字，绝对不要返回对象或数组。sprite_id必须属于speaker_id，旁白不得携带立绘，状态变化必须使用已定义ID。
    """.trimIndent()

    private fun parse(raw: String, old: GameState, assets: ProjectAssets): GameState {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val clean = trimmed.substringAfter('{', trimmed).substringBeforeLast('}', trimmed).let { if (it == trimmed) it else "{$it}" }
        val json = JSONObject(clean)
        val backgroundId = json.optString("background_id").ifBlank { null }
        val allowedBackgrounds = old.project.chapters.find { it.id == old.chapter }?.allowedBackgroundIds.orEmpty()
        require(backgroundId == null || allowedBackgrounds.isEmpty() || backgroundId in allowedBackgrounds) { "background is not allowed in this chapter" }
        val lines = json.getJSONArray("lines").let { array ->
            List(array.length()) { index ->
                val line = array.getJSONObject(index)
                val id = line.optString("speaker_id")
                val allowed = old.project.chapters.find { it.id == old.chapter }?.allowedCharacterIds ?: listOf("aya")
                require(id.isBlank() || id in allowed) { "character is not allowed in this chapter" }
                if (id == old.project.playerCharacterId) return@List null
                val requestedSpriteId = line.optString("sprite_id").ifBlank { null }
                require(id.isNotBlank() || requestedSpriteId == null) { "narration cannot have a sprite" }
                val characterAssetIds = assets.sprites.filter { it.characterId == id }.map { it.id }.toSet()
                val defaultSpriteId = assets.sprites.firstOrNull { it.characterId == id }?.id
                val spriteId = requestedSpriteId?.takeIf { it in characterAssetIds } ?: defaultSpriteId
                val speakerName = if (id.isBlank()) "旁白" else old.project.characters.find { it.id == id }?.name ?: id
                DialogueLine(id, speakerName, line.getString("text").trim().take(120), spriteId)
            }.filterNotNull().filter { it.text.isNotBlank() }.take(3)
        }
        require(lines.isNotEmpty()) { "AI returned empty lines" }
        val choices = json.optJSONArray("choices")?.let { array ->
            List(array.length()) { index -> limitChoice(choiceText(array.opt(index))) }.filter(String::isNotBlank).take(3)
        }.orEmpty().ifEmpty { defaultChoices(old) }
        val deltas = json.optJSONObject("variable_deltas")?.let { obj -> old.project.variables.associate { variable -> variable.id to obj.optInt(variable.id, 0).coerceIn(variable.perTurnMinimum, variable.perTurnMaximum) } }.orEmpty()
        val nextValues = old.variables.toMutableMap()
        deltas.forEach { (id, delta) -> old.project.variables.find { it.id == id }?.let { variable -> nextValues[id] = ((nextValues[id] ?: variable.initial) + delta).coerceIn(variable.minimum, variable.maximum) } }
        val narration = json.optString("narration").trim().take(120)
        val displayLines = if (narration.isBlank()) lines else listOf(DialogueLine("", "旁白", narration)) + lines
        val firstLine = displayLines.first()
        val firstIsNarration = firstLine.speakerId.isBlank()
        val progressJson = json.optJSONObject("chapter_progress")
        val progress = progressJson?.let { ProgressReport(it.optString("reason"), it.optString("status", "in_progress"), it.optBoolean("completion"), emptyList(), "", emptyList(), it.optString("summary").take(600), it.optJSONArray("key_facts")?.let { values -> List(values.length()) { index -> values.optString(index).take(200) }.filter(String::isNotBlank) }.orEmpty(), it.optJSONArray("relationship_notes")?.let { values -> List(values.length()) { index -> values.optString(index).take(200) }.filter(String::isNotBlank) }.orEmpty()) }
        return old.copy(
            variables = nextValues,
            affection = nextValues["affection_aya"] ?: old.affection,
            items = applyIds(old.items, json.optJSONArray("grant_item_ids"), json.optJSONArray("remove_item_ids"), old.project.items.map { it.id }.toSet()),
            events = applyIds(old.events, json.optJSONArray("set_event_ids"), json.optJSONArray("clear_event_ids"), old.project.events.map { it.id }.toSet()),
            hasMap = "island_map" in applyIds(old.items, json.optJSONArray("grant_item_ids"), json.optJSONArray("remove_item_ids"), old.project.items.map { it.id }.toSet()),
            agreed = "agreed_to_island" in applyIds(old.events, json.optJSONArray("set_event_ids"), json.optJSONArray("clear_event_ids"), old.project.events.map { it.id }.toSet()),
            goal = json.optString("goal", old.goal).take(80),
            messages = displayLines.map { StoryMessage(it.speaker, it.text) },
            lines = displayLines,
            lineIndex = 0,
            choices = choices,
            chapterProgress = progress,
            // The first line is rendered immediately; narration must never inherit a prior sprite.
            scene = old.scene.copy(backgroundId = backgroundId ?: old.scene.backgroundId, visibleCharacterId = if (firstIsNarration) null else firstLine.speakerId, visibleSpriteId = if (firstIsNarration) null else firstLine.spriteId)
        )
    }

    private fun applyIds(old: Set<String>, additions: JSONArray?, removals: JSONArray?, allowed: Set<String>): Set<String> {
        val result = old.toMutableSet()
        additions?.let { array -> for (i in 0 until array.length()) array.optString(i).takeIf { it in allowed }?.let(result::add) }
        removals?.let { array -> for (i in 0 until array.length()) result.remove(array.optString(i)) }
        return result
    }

    private fun limitChoice(value: String): String { val text = value.trim(); return if (text.length > 15) text.take(14) + "..." else text }

    private fun defaultChoices(state: GameState): List<String> {
        val npc = state.project.characters.firstOrNull { it.id != state.project.playerCharacterId && it.id in state.chapterConfig()?.allowedCharacterIds.orEmpty() }
        return listOf(npc?.let { "继续与${it.name}交谈" } ?: "继续观察周围", "询问当前情况", "表达自己的想法").map(::limitChoice)
    }

    private fun choiceText(value: Any?): String = when (value) {
        is JSONObject -> listOf("text", "label", "title", "value").firstNotNullOfOrNull { key -> value.optString(key).takeIf(String::isNotBlank) }.orEmpty()
        null -> ""
        else -> value.toString()
    }
}
