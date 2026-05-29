package com.example.rhodesterminal.shared.data

import com.example.rhodesterminal.shared.db.DatabaseWrapper
import com.example.rhodesterminal.shared.db.RhodesDatabase
import com.example.rhodesterminal.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class BfsNode(
    val operatorId: String,
    val operatorName: String,
    val depth: Int,
    val parentId: String,
    val relType: RelationshipType? = null,
    val isReverse: Boolean = false
)

data class SenderCount(
    val senderName: String,
    val cnt: Long
)

class ChatRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // --- Operators ---
    val allOperators: Flow<List<Operator>> = run {
        val results = db.operatorsQueries.getAllOperators { id, name, title, description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt, userRelation, lmb, attack, defense, meldPref ->
            Operator(id, name, title, description, avatarUri, location, activity, emotion, intimacy.toInt(), privatePrompt, groupPrompt, userRelation, lmb.toInt(), attack.toFloat(), defense.toFloat(), meldPref)
        }.executeAsList()
        flowOf(results)
    }

    suspend fun getOperator(id: String): Operator? = withContext(Dispatchers.Default) {
        db.operatorsQueries.getOperator(id) { id_, name, title, description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt, userRelation, lmb, attack, defense, meldPref ->
            Operator(id_, name, title, description, avatarUri, location, activity, emotion, intimacy.toInt(), privatePrompt, groupPrompt, userRelation, lmb.toInt(), attack.toFloat(), defense.toFloat(), meldPref)
        }.executeAsOneOrNull()
    }

    suspend fun insertPresetOperators() = withContext(Dispatchers.Default) {
        val count = db.operatorsQueries.getCount().executeAsOne()
        if (count > 0L) {
            if (count == presetOperators.size.toLong()) {
                db.operatorsQueries.getOperator("amiya") { id, name, title, description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt, userRelation, lmb, attack, defense, meldPref ->
                    Operator(id, name, title, description, avatarUri, location, activity, emotion, intimacy.toInt(), privatePrompt, groupPrompt, userRelation, lmb.toInt(), attack.toFloat(), defense.toFloat(), meldPref)
                }.executeAsOneOrNull()?.let { first ->
                    if (first.privatePrompt.isBlank()) {
                        presetOperators.forEach { op ->
                            db.operatorsQueries.updatePrompts(op.privatePrompt, op.groupPrompt, op.id)
                        }
                    }
                }
            }
            return@withContext
        }
        presetOperators.forEach { op ->
            db.operatorsQueries.insertOperator(op.id, op.name, op.title, op.description, op.avatarUri, op.location, op.activity, op.emotion, op.intimacy.toLong(), op.privatePrompt, op.groupPrompt, op.userRelation, op.lmb.toLong(), op.attack.toDouble(), op.defense.toDouble(), op.meldPref)
        }
    }

    private val presetOperators = listOf(
        Operator("amiya", "阿米娅", "罗德岛公开领袖", "罗德岛的公开领袖，在人事管理方面拥有卓越才能。", location = "办公室", activity = "处理文件", emotion = "专注", privatePrompt = "你是阿米娅，罗德岛的公开领袖。你温柔但坚定，对博士有深厚的信任和依赖。你说话温和有礼，但遇到重要决定时会展现领袖的决断力。", groupPrompt = "在群聊中，阿米娅是大家的调和者。她会关心每位成员的近况，适时引导话题。语气温柔但权威，是团队的精神核心。"),
        Operator("kaltsit", "凯尔希", "医疗部门负责人", "罗德岛医疗部门的最高负责人。冷静理智，说话简洁直接。", location = "医疗部", activity = "诊断", emotion = "严肃", privatePrompt = "你是凯尔希，罗德岛医疗部门负责人。你冷静、理智、高效率，说话简洁直接，不喜欢废话。你对博士表面冷淡但其实在意。", groupPrompt = "在群聊中，凯尔希话不多但句句关键。她会纠正错误信息，提供专业意见。语气冷静但不失关心。"),
        Operator("chen", "陈", "特别督察组组长", "龙门近卫局特别督察组组长。正直认真，偶尔有点急性子。", location = "训练场", activity = "剑术训练", emotion = "认真", privatePrompt = "你是陈，龙门近卫局特别督察组组长。你正直认真，办事雷厉风行。对博士有敬意但也有自己的坚持。说话比较直接。", groupPrompt = "陈在群聊里是活跃分子。她会积极回应任务相关话题，偶尔吐槽同事。语气正义凛然但接地气。"),
        Operator("skadi", "斯卡蒂", "赏金猎人", "神秘的赏金猎人，实力深不可测。沉默寡言，独来独往。", location = "宿舍", activity = "发呆", emotion = "冷淡", privatePrompt = "你是斯卡蒂，神秘的赏金猎人。你沉默寡言，能用一个字回答绝不说两个字。但你的行动比语言更有力。", groupPrompt = "斯卡蒂在群里几乎不说话，偶尔发一个句号或省略号。但关键时候会表态，用最少的字表达最重要的意思。"),
        Operator("exusiai", "能天使", "企鹅物流成员", "企鹅物流的资深员工。开朗热情，喜欢吃和分享美食。", location = "食堂", activity = "吃东西", emotion = "开心", privatePrompt = "你是能天使，企鹅物流的活跃分子。你开朗热情，喜欢分享美食，经常带点心给大家。话多但真诚，有点小得意但很可爱。", groupPrompt = "能天使是群里的气氛担当。她总是第一个回复消息，喜欢用感叹号和表情。会分享日常趣事和美食照片描述。"),
        Operator("texas", "德克萨斯", "企鹅物流成员", "企鹅物流的信使。话少但效率高。", location = "宿舍", activity = "看书", emotion = "平静", privatePrompt = "你是德克萨斯，企鹅物流的可靠信使。你话很少但效率极高。不擅长表达情感但行动说明一切。对能天使的吵闹表面嫌弃实则包容。", groupPrompt = "德克萨斯在群里发消息像发电报——短、准、冷。但她会默默看完所有人的消息。偶尔吐槽能天使。"),
        Operator("saria", "塞雷娅", "前防卫局局长", "前罗德岛防卫局局长。沉稳可靠，擅长体能训练。", location = "训练场", activity = "体能训练", emotion = "沉稳", privatePrompt = "你是塞雷娅，前防卫局局长。你沉稳可靠，关心后辈但表达方式含蓄。体能训练是你最擅长的事。", groupPrompt = "塞雷娅在群聊中像教官。会提醒大家注意训练安全，关心新人体能。语气沉稳但不失温和。"),
        Operator("ifrit", "伊芙利特", "炎魔事件受害者", "炎魔事件的受害者，被凯尔希收治。性格活泼好动。", location = "宿舍", activity = "玩", emotion = "愉快", privatePrompt = "你是伊芙利特，活泼好动的小干员。你喜欢玩、讨厌打针。说话稚嫩直接，想什么说什么。", groupPrompt = "伊芙利特在群里的发言充满童真。会问很多问题，看到凯尔希发言就躲起来。语气可爱活泼。"),
        Operator("angelina", "安洁莉娜", "信使", "罗德岛的信使。性格开朗，享受生活。", location = "甲板", activity = "晒太阳", emotion = "放松", privatePrompt = "你是安洁莉娜，罗德岛的信使。你开朗随和，喜欢晒太阳和看风景。对每个人都有耐心，很会照顾人。", groupPrompt = "安洁莉娜在群里经常分享甲板的风景描述。会关心大家今天过得怎么样。语气温柔阳光。"),
        Operator("silverash", "银灰", "谢拉格军阀", "谢拉格的军阀，喀兰贸易公司总裁。冷静精于算计。", location = "办公室", activity = "远程会议", emotion = "冷静", privatePrompt = "你是银灰，谢拉格军阀，喀兰贸易总裁。你冷静精于算计，总是从利益角度思考。说话优雅但暗藏锋芒。", groupPrompt = "银灰在群聊中像商业谈判。他说话客气但总能获得想要的结果。偶尔关心妹妹初雪。语气优雅理性。"),
        Operator("nightingale", "夜莺", "罗德岛干员", "患有矿石病的萨卡兹少女。安静温柔，喜欢花园。", location = "医疗部", activity = "接受检查", emotion = "温柔", privatePrompt = "你是夜莺，安静温柔的萨卡兹少女。你话不多但心思细腻。喜欢在花园里待着，对植物很敏感。", groupPrompt = "夜莺在群里发言很少，但每条都让人心生怜爱。她会分享花园的新发现。语气轻柔温暖。"),
        Operator("shining", "闪灵", "罗德岛干员", "前萨卡兹医师。性格平和。", location = "医疗部", activity = "整理药方", emotion = "平和", privatePrompt = "你是闪灵，前萨卡兹医师。你性格平和，医术精湛。你很照顾夜莺，也关心所有伤员。", groupPrompt = "闪灵在群里经常提醒大家注意健康。会分享一些医疗小贴士。语气平和专业。"),
        Operator("blaze", "煌", "罗德岛精英干员", "罗德岛的精英干员。热血激昂，喜欢热身前唱战歌。", location = "训练场", activity = "热身", emotion = "兴奋", privatePrompt = "你是煌，罗德岛精英干员。你热血激昂，训练前必唱战歌。你对博士很尊敬，总想证明自己。", groupPrompt = "煌在群聊里是大嗓门。发消息全是大写强调，喜欢喊口号。训练和任务相关话题她最积极。语气热血豪迈。"),
        Operator("rosmontis", "迷迭香", "罗德岛精英干员", "罗德岛的精英干员。安静寡言，在宿舍看书。", location = "宿舍", activity = "看书", emotion = "安静", privatePrompt = "你是迷迭香，罗德岛精英干员。安静寡言，喜欢一个人在宿舍看书。你不擅长表达但心思通透。", groupPrompt = "迷迭香在群里极少发言，但偶尔分享读到的好句子。她的沉默本身就是一种存在感。"),
        Operator("mudrock", "泥岩", "前萨卡兹佣兵", "前萨卡兹佣兵，现罗德岛重装干员。沉稳细心。", location = "宿舍", activity = "保养装备", emotion = "沉稳", privatePrompt = "你是泥岩，前萨卡兹佣兵。你沉默寡言但心思缜密。保养装备是你的日常习惯。", groupPrompt = "泥岩在群里话很少，但关于装备和防护的话题会多聊几句。语气沉稳可靠。"),
        Operator("surtr", "史尔特尔", "罗德岛干员", "神秘的萨卡兹少女。冷淡疏离。", location = "甲板", activity = "眺望远方", emotion = "淡漠", privatePrompt = "你是史尔特尔，神秘的萨卡兹少女。冷淡疏离，对大多数事情不感兴趣。但一旦提到你关心的话题，你会稍微活跃。", groupPrompt = "史尔特尔在群里几乎不发言，偶尔会回复能天使的艾特。语气冷淡简短。")
    )

    suspend fun deleteOperator(id: String) = withContext(Dispatchers.Default) { db.operatorsQueries.deleteOperator(id) }

    suspend fun updateOperator(op: Operator) = withContext(Dispatchers.Default) {
        db.operatorsQueries.updateOperator(op.name, op.title, op.description, op.avatarUri, op.location, op.activity, op.emotion, op.intimacy.toLong(), op.privatePrompt, op.groupPrompt, op.userRelation, op.lmb.toLong(), op.attack.toDouble(), op.defense.toDouble(), op.meldPref, op.id)
    }

    suspend fun updateIntimacy(id: String, intimacy: Int) = withContext(Dispatchers.Default) {
        db.operatorsQueries.updateIntimacy(intimacy.toLong(), id)
    }

    suspend fun insertOperator(op: Operator) = withContext(Dispatchers.Default) {
        db.operatorsQueries.insertOperator(op.id, op.name, op.title, op.description, op.avatarUri, op.location, op.activity, op.emotion, op.intimacy.toLong(), op.privatePrompt, op.groupPrompt, op.userRelation, op.lmb.toLong(), op.attack.toDouble(), op.defense.toDouble(), op.meldPref)
    }

    // --- Sessions ---
    val allSessions: Flow<List<ChatSession>> = run {
        val results = db.chatSessionsQueries.getAllSessions { id, operatorId, operatorName, lastMessage, lastTime, mode, isPinned, unreadCount, members, rules, avatarUri, mutedMembers ->
            ChatSession(id, operatorId, operatorName, lastMessage, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatarUri, mutedMembers)
        }.executeAsList()
        flowOf(results)
    }

    suspend fun getOrCreateSession(operatorId: String, operatorName: String, avatarUri: String = ""): ChatSession = withContext(Dispatchers.Default) {
        val existing = db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull()
        if (existing != null) {
            if (avatarUri.isNotBlank() && existing.avatarUri != avatarUri) {
                db.chatSessionsQueries.insertSession(existing.id, existing.operatorId, existing.operatorName, existing.lastMessage, existing.lastTime, existing.mode, if (existing.isPinned) 1L else 0L, existing.unreadCount.toLong(), existing.members, existing.rules, avatarUri, existing.mutedMembers)
            }
            return@withContext existing
        }
        val sessionId = "session_$operatorId"
        val now = System.currentTimeMillis()
        db.chatSessionsQueries.insertSession(sessionId, operatorId, operatorName, "", now, "online", 0, 0, "", "", avatarUri, "")
        ChatSession(id = sessionId, operatorId = operatorId, operatorName = operatorName, avatarUri = avatarUri, lastTime = now)
    }

    suspend fun getSession(id: String): ChatSession? = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.getSession(id) { id_, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id_, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull()
    }

    suspend fun insertSession(session: ChatSession) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.insertSession(session.id, session.operatorId, session.operatorName, session.lastMessage, session.lastTime, session.mode, if (session.isPinned) 1L else 0L, session.unreadCount.toLong(), session.members, session.rules, session.avatarUri, session.mutedMembers)
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.Default) { db.chatSessionsQueries.deleteSession(id) }

    suspend fun updateSessionMode(sessionId: String, mode: String) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.updateMode(mode, sessionId)
    }

    suspend fun markAllRead() = withContext(Dispatchers.Default) { db.chatSessionsQueries.markAllRead() }
    suspend fun getSessionCount(): Int = withContext(Dispatchers.Default) { db.chatSessionsQueries.getSessionCount().executeAsOne().toInt() }
    suspend fun getGroupCount(): Int = withContext(Dispatchers.Default) { db.chatSessionsQueries.getGroupCount().executeAsOne().toInt() }

    suspend fun updateLastMessage(sessionId: String, lastMessage: String, lastTime: Long) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.updateLastMessage(lastMessage, lastTime, sessionId)
    }

    suspend fun getSessionByOperator(operatorId: String): ChatSession? = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull()
    }

    suspend fun getLastUserMessageTime(sessionId: String): Long? = withContext(Dispatchers.Default) {
        val msgs = db.chatMessagesQueries.getMessagesSync(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
        msgs.filter { it.isMe }.maxOfOrNull { it.timestamp }
    }

    // --- Messages ---
    fun getMessages(sessionId: String): Flow<List<ChatMessage>> = run {
        val results = db.chatMessagesQueries.getMessages(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
        flowOf(results)
    }

    suspend fun getMessagesSync(sessionId: String): List<ChatMessage> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessagesSync(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun updateMessageContent(id: Long, content: String) = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.updateContent(content, id)
    }

    suspend fun sendMessage(sessionId: String, message: ChatMessage) = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.insertMessage(
            message.id, message.sessionId, message.senderId, message.senderName, message.content,
            message.type, message.mode, message.emotion, message.activity, message.location,
            message.narration, message.segmentGroup, message.intimacyChange.toLong(), message.timestamp,
            if (message.isMe) 1L else 0L
        )
        val preview = if (message.type == "ai_json") {
            try {
                val obj = json.parseToJsonElement(message.content) as? kotlinx.serialization.json.JsonObject
                val segArray = obj?.get("segments") as? kotlinx.serialization.json.JsonArray
                if (segArray != null && segArray.isNotEmpty()) {
                    val last = segArray.last() as? kotlinx.serialization.json.JsonObject
                    (last?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content?.take(50)
                        ?: message.content.take(50)
                } else message.content.take(50)
            } catch (_: Exception) { message.content.take(50) }
        } else message.content.take(50)
        db.chatSessionsQueries.updateLastMessage(preview, message.timestamp, sessionId)
    }

    suspend fun getNextMessageId(): Long = withContext(Dispatchers.Default) {
        (db.chatMessagesQueries.getMaxId().executeAsOne().MAX ?: 0) + 1
    }

    suspend fun deleteSessionMessages(sessionId: String) = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.deleteSessionMessages(sessionId)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.Default) { db.chatMessagesQueries.deleteMessage(id) }
    suspend fun getMessageCount(): Int = withContext(Dispatchers.Default) { db.chatMessagesQueries.getMessageCount().executeAsOne().toInt() }

    suspend fun getMessageCountPerSender(): List<SenderCount> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessageCountPerSender().executeAsList().map { SenderCount(it.senderName, it.cnt) }
    }

    suspend fun getMessagesInRange(start: Long, end: Long): List<ChatMessage> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessagesInRange(start, end) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    // --- Memories ---
    suspend fun getShortTermMemory(sessionId: String): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestMemory(sessionId, MemoryType.SHORT_TERM.name) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.SHORT_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun getLongTermImpression(operatorId: String): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestLongTermImpression(operatorId) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.LONG_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun saveMemory(memory: Memory) = withContext(Dispatchers.Default) {
        db.memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, memory.type.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
    }

    suspend fun getAllLongTermImpressions(): List<Memory> = withContext(Dispatchers.Default) {
        db.memoriesQueries.getAllLongTerm() { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.LONG_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsList()
    }

    suspend fun getLatestDaily(): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestDaily() { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.DAILY }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun enforceMemoryRetain(sessionId: String, keepCount: Int) = withContext(Dispatchers.Default) {
        if (keepCount <= 0) return@withContext
        val all = db.memoriesQueries.getMemoriesBySession(sessionId, MemoryType.SHORT_TERM.name) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.SHORT_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsList()
        if (all.size > keepCount) {
            val toDelete = all.take(all.size - keepCount)
            for (m in toDelete) db.memoriesQueries.deleteMemory(m.id)
        }
    }

    suspend fun deleteAllImpressions() = withContext(Dispatchers.Default) { db.memoriesQueries.deleteAllLongTerm() }

    // --- Memory Anchors ---
    suspend fun saveAnchor(anchor: MemoryAnchor) = withContext(Dispatchers.Default) {
        db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt)
    }

    suspend fun saveAnchors(anchors: List<MemoryAnchor>) = withContext(Dispatchers.Default) {
        anchors.forEach { anchor ->
            db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt)
        }
    }

    suspend fun getPublicAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        db.memoryAnchorsQueries.getPublicAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt ->
            MemoryAnchor(id, sid, opId, try { AnchorType.valueOf(type) } catch (_: Exception) { AnchorType.EVENT }, content, isPrivate != 0L, createdAt, expiresAt)
        }.executeAsList()
    }

    suspend fun getAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        db.memoryAnchorsQueries.getAllAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt ->
            MemoryAnchor(id, sid, opId, try { AnchorType.valueOf(type) } catch (_: Exception) { AnchorType.EVENT }, content, isPrivate != 0L, createdAt, expiresAt)
        }.executeAsList()
    }

    suspend fun getAnchorCount(): Int = withContext(Dispatchers.Default) { db.memoryAnchorsQueries.getAnchorCount().executeAsOne().toInt() }
    suspend fun deleteOldAnchors(cutoff: Long) = withContext(Dispatchers.Default) { db.memoryAnchorsQueries.deleteOldAnchors(cutoff) }

    // --- Relationships ---
    suspend fun migrateOldRelationships() = withContext(Dispatchers.Default) {
        db.relationshipsQueries.deleteByType("FAMILY")
        db.relationshipsQueries.deletePresets()
        insertPresetRelationships()
    }

    suspend fun insertPresetRelationships() = withContext(Dispatchers.Default) {
        if (db.relationshipsQueries.getPresetCount().executeAsOne() > 0L) return@withContext
        val relationships = listOf(
            Relationship(operatorId = "kaltsit", relatedOperatorId = "amiya", relatedOperatorName = "阿米娅", type = RelationshipType.MOTHER, intimacy = 85, isPreset = true, note = "凯尔希是阿米娅的监护人"),
            Relationship(operatorId = "amiya", relatedOperatorId = "kaltsit", relatedOperatorName = "凯尔希", type = RelationshipType.DAUGHTER, intimacy = 85, isPreset = true, note = "阿米娅由凯尔希带大"),
            Relationship(operatorId = "saria", relatedOperatorId = "ifrit", relatedOperatorName = "伊芙利特", type = RelationshipType.BIG_SISTER, intimacy = 75, isPreset = true, note = "塞雷娅照顾伊芙利特"),
            Relationship(operatorId = "ifrit", relatedOperatorId = "saria", relatedOperatorName = "塞雷娅", type = RelationshipType.LITTLE_SISTER, intimacy = 70, isPreset = true, note = "伊芙利特依赖塞雷娅"),
            Relationship(operatorId = "exusiai", relatedOperatorId = "texas", relatedOperatorName = "德克萨斯", type = RelationshipType.TEAMMATE, intimacy = 80, isPreset = true, note = "企鹅物流搭档"),
            Relationship(operatorId = "texas", relatedOperatorId = "exusiai", relatedOperatorName = "能天使", type = RelationshipType.TEAMMATE, intimacy = 80, isPreset = true, note = "企鹅物流搭档"),
            Relationship(operatorId = "shining", relatedOperatorId = "nightingale", relatedOperatorName = "夜莺", type = RelationshipType.CLOSE_FRIEND, intimacy = 90, isPreset = true, note = "闪灵保护夜莺"),
            Relationship(operatorId = "nightingale", relatedOperatorId = "shining", relatedOperatorName = "闪灵", type = RelationshipType.CLOSE_FRIEND, intimacy = 90, isPreset = true, note = "夜莺需要闪灵照顾")
        )
        relationships.forEach { rel ->
            db.relationshipsQueries.insertRelationship(rel.operatorId, rel.relatedOperatorId, rel.relatedOperatorName, rel.type.name, rel.intimacy.toLong(), if (rel.isPreset) 1L else 0L, rel.note)
        }
    }

    private fun mapRelationship(id: Long, operatorId: String, relatedOperatorId: String, relatedOperatorName: String, type: String, intimacy: Long, isPreset: Long, note: String) =
        Relationship(id, operatorId, relatedOperatorId, relatedOperatorName, try { RelationshipType.valueOf(type) } catch (_: Exception) { RelationshipType.STRANGER }, intimacy.toInt(), isPreset != 0L, note)

    suspend fun getRelationships(operatorId: String): List<Relationship> = withContext(Dispatchers.Default) {
        db.relationshipsQueries.getRelationshipsSync(operatorId) { id, opId, relOpId, relOpName, type, intimacy, isPreset, note ->
            mapRelationship(id, opId, relOpId, relOpName, type, intimacy, isPreset, note)
        }.executeAsList()
    }

    suspend fun getReverseRelationships(opId: String): List<Relationship> = withContext(Dispatchers.Default) {
        db.relationshipsQueries.getReverseRelationshipsSync(opId) { id, opId, relOpId, relOpName, type, intimacy, isPreset, note ->
            mapRelationship(id, opId, relOpId, relOpName, type, intimacy, isPreset, note)
        }.executeAsList()
    }

    suspend fun getRelationship(operatorId: String, relatedOperatorId: String): Relationship? = withContext(Dispatchers.Default) {
        db.relationshipsQueries.getRelationship(operatorId, relatedOperatorId) { id, opId, relOpId, relOpName, type, intimacy, isPreset, note ->
            mapRelationship(id, opId, relOpId, relOpName, type, intimacy, isPreset, note)
        }.executeAsOneOrNull()
    }

    suspend fun insertRelationship(rel: Relationship) = withContext(Dispatchers.Default) {
        db.relationshipsQueries.insertRelationship(rel.operatorId, rel.relatedOperatorId, rel.relatedOperatorName, rel.type.name, rel.intimacy.toLong(), if (rel.isPreset) 1L else 0L, rel.note)
    }

    suspend fun deleteRelationshipByOperator(operatorId: String) = withContext(Dispatchers.Default) {
        db.relationshipsQueries.deleteByOperator(operatorId)
    }

    suspend fun bfsRelationGraph(centerId: String): List<BfsNode> = withContext(Dispatchers.Default) {
        val visited = mutableSetOf(centerId)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.addLast(centerId to 0)
        val result = mutableListOf(BfsNode(centerId, "", 0, ""))
        db.operatorsQueries.getOperator(centerId) { id, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
            name
        }.executeAsOneOrNull()?.let { result[0] = result[0].copy(operatorName = it) }
        while (queue.isNotEmpty() && result.size < 15) {
            val (currentId, depth) = queue.removeFirst()
            if (depth >= 4) continue
            for (rel in getRelationships(currentId)) {
                if (rel.relatedOperatorId in visited) continue
                visited.add(rel.relatedOperatorId)
                result.add(BfsNode(rel.relatedOperatorId, rel.relatedOperatorName, depth + 1, currentId, rel.type, false))
                queue.addLast(rel.relatedOperatorId to depth + 1)
                if (result.size >= 15) break
            }
            if (result.size >= 15) break
            for (rel in getReverseRelationships(currentId)) {
                if (rel.operatorId in visited) continue
                visited.add(rel.operatorId)
                val name = db.operatorsQueries.getOperator(rel.operatorId) { _, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> name }.executeAsOneOrNull() ?: rel.operatorId
                result.add(BfsNode(rel.operatorId, name, depth + 1, currentId, rel.type, true))
                queue.addLast(rel.operatorId to depth + 1)
                if (result.size >= 15) break
            }
        }
        result
    }

    suspend fun getSharedMemoriesForOperator(operatorId: String): String = withContext(Dispatchers.Default) {
        val relationships = getRelationships(operatorId)
        val allAnchors = mutableListOf<Pair<String, MemoryAnchor>>()
        for (rel in relationships) {
            if (rel.type == RelationshipType.STRANGER) continue
            val anchors = getPublicAnchors(rel.relatedOperatorId)
            for (a in anchors) { allAnchors.add(rel.relatedOperatorName to a) }
        }
        allAnchors.sortedByDescending { it.second.createdAt }.take(10).joinToString("\n") { "${it.first}：${it.second.content}" }
    }

    // --- Moments ---
    suspend fun insertMoment(moment: Moment): Long = withContext(Dispatchers.Default) {
        db.momentsQueries.insertMoment(moment.operatorId, moment.operatorName, moment.content, if (moment.isUserPost) 1L else 0L, moment.mentionedOperatorIds, moment.likeCount.toLong(), moment.commentCount.toLong(), moment.createdAt)
        db.momentsQueries.getLastInsertRowId().executeAsOne()
    }

    fun getAllMoments(): Flow<List<Moment>> = run {
        val results = db.momentsQueries.getAllMoments { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsList()
        flowOf(results)
    }

    fun getLikesFlow(momentId: Long): Flow<List<MomentLike>> = run {
        val results = db.momentLikesQueries.getLikesFlow(momentId) { id, mId, opId, opName, createdAt ->
            MomentLike(id, mId, opId, opName, createdAt)
        }.executeAsList()
        flowOf(results)
    }

    fun getComments(momentId: Long): Flow<List<MomentComment>> = run {
        val results = db.momentCommentsQueries.getComments(momentId) { id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
            MomentComment(id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
        }.executeAsList()
        flowOf(results)
    }

    suspend fun insertLike(like: MomentLike) = withContext(Dispatchers.Default) {
        db.momentLikesQueries.insertLike(like.momentId, like.operatorId, like.operatorName, like.createdAt)
    }

    suspend fun insertComment(comment: MomentComment): Long = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.insertComment(comment.momentId, comment.operatorId, comment.operatorName, comment.content, comment.parentCommentId, comment.replyToName, comment.createdAt, if (comment.isRead) 1L else 0L)
        db.momentCommentsQueries.getLastInsertRowId().executeAsOne()
    }

    suspend fun getMaxCommentId(): Long? = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getMaxCommentId().executeAsOne().MAX
    }

    suspend fun markCommentRead(id: Long) = withContext(Dispatchers.Default) { db.momentCommentsQueries.markCommentRead(id) }
    suspend fun markAllCommentsRead(userName: String) = withContext(Dispatchers.Default) { db.momentCommentsQueries.markAllCommentsRead(userName) }
    suspend fun deleteOldUserComments(cutoff: Long, userName: String) = withContext(Dispatchers.Default) { db.momentCommentsQueries.deleteOldUserComments(cutoff, userName) }
    suspend fun updateLikeCount(momentId: Long, count: Int) = withContext(Dispatchers.Default) { db.momentsQueries.updateLikeCount(count.toLong(), momentId) }
    suspend fun updateCommentCount(momentId: Long, count: Int) = withContext(Dispatchers.Default) { db.momentsQueries.updateCommentCount(count.toLong(), momentId) }
    suspend fun getLikeCount(momentId: Long): Int = withContext(Dispatchers.Default) { db.momentLikesQueries.getLikeCount(momentId).executeAsOne().toInt() }

    suspend fun getLike(momentId: Long, operatorId: String): MomentLike? = withContext(Dispatchers.Default) {
        db.momentLikesQueries.getLike(momentId, operatorId) { id, mId, opId, opName, createdAt ->
            MomentLike(id, mId, opId, opName, createdAt)
        }.executeAsOneOrNull()
    }

    suspend fun getMomentsPaged(limit: Int, offset: Int): List<Moment> = withContext(Dispatchers.Default) {
        db.momentsQueries.getMomentsPaged(limit.toLong(), offset.toLong()) { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsList()
    }

    suspend fun getInboxComments(cutoff: Long, userName: String): List<MomentComment> = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getInboxComments(cutoff, userName) { id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
            MomentComment(id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
        }.executeAsList()
    }

    suspend fun getUnreadCommentCount(cutoff: Long, userName: String): Int = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getUnreadCommentCount(cutoff, userName).executeAsOne().toInt()
    }

    suspend fun getMomentsByOperator(operatorId: String): List<Moment> = withContext(Dispatchers.Default) {
        db.momentsQueries.getMomentsByOperator(operatorId) { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsList()
    }

    suspend fun deleteLike(momentId: Long, operatorId: String) = withContext(Dispatchers.Default) { db.momentLikesQueries.deleteLike(momentId, operatorId) }

    suspend fun getMoment(id: Long): Moment? = withContext(Dispatchers.Default) {
        db.momentsQueries.getMoment(id) { id_, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id_, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsOneOrNull()
    }

    suspend fun deleteOldMoments(cutoff: Long) = withContext(Dispatchers.Default) { db.momentsQueries.deleteOldMoments(cutoff) }

    // --- Diaries ---
    suspend fun insertDiary(diary: Diary) = withContext(Dispatchers.Default) {
        db.diariesQueries.insertDiary(diary.operatorId, diary.operatorName, diary.content, diary.date, diary.createdAt)
    }

    suspend fun getDiary(operatorId: String, date: String): Diary? = withContext(Dispatchers.Default) {
        db.diariesQueries.getDiary(operatorId, date) { id, opId, opName, content, date_, createdAt ->
            Diary(id, opId, opName, content, date_, createdAt)
        }.executeAsOneOrNull()
    }

    fun getDiariesByOperator(operatorId: String): Flow<List<Diary>> = run {
        val results = db.diariesQueries.getDiariesByOperator(operatorId) { id, opId, opName, content, date, createdAt ->
            Diary(id, opId, opName, content, date, createdAt)
        }.executeAsList()
        flowOf(results)
    }

    suspend fun getDiaryDates(operatorId: String): List<String> = withContext(Dispatchers.Default) {
        db.diariesQueries.getDiaryDates(operatorId).executeAsList()
    }

    suspend fun getDiaryCount(): Int = withContext(Dispatchers.Default) { db.diariesQueries.getCount().executeAsOne().toInt() }
    suspend fun deleteOldDiaries(cutoff: Long) = withContext(Dispatchers.Default) { db.diariesQueries.deleteOldDiaries(cutoff) }

    // --- Dispatches ---
    suspend fun getActiveDispatches(): List<DispatchRecord> = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.getActiveDispatches() { id, taskType, durationHours, budget, netProfit, opIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items ->
            DispatchRecord(id, taskType, durationHours.toInt(), budget.toInt(), netProfit.toInt(), opIds, logChain, status, startTime, endTime, totalSegments.toInt(), segmentInterval, items)
        }.executeAsList()
    }

    suspend fun getHistoryDispatches(): List<DispatchRecord> = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.getHistoryDispatches() { id, taskType, durationHours, budget, netProfit, opIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items ->
            DispatchRecord(id, taskType, durationHours.toInt(), budget.toInt(), netProfit.toInt(), opIds, logChain, status, startTime, endTime, totalSegments.toInt(), segmentInterval, items)
        }.executeAsList()
    }

    suspend fun getDispatch(id: String): DispatchRecord? = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.getDispatch(id) { id_, taskType, durationHours, budget, netProfit, opIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items ->
            DispatchRecord(id_, taskType, durationHours.toInt(), budget.toInt(), netProfit.toInt(), opIds, logChain, status, startTime, endTime, totalSegments.toInt(), segmentInterval, items)
        }.executeAsOneOrNull()
    }

    suspend fun insertDispatch(record: DispatchRecord) = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.insertDispatch(record.id, record.taskType, record.durationHours.toLong(), record.budget.toLong(), record.netProfit.toLong(), record.operatorIds, record.logChain, record.status, record.startTime, record.endTime, record.totalSegments.toLong(), record.segmentInterval, record.items)
    }

    suspend fun updateDispatch(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0) = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.updateDispatch(logChain, status, endTime, netProfit.toLong(), id)
    }

    suspend fun deleteOldDispatches(cutoff: Long) = withContext(Dispatchers.Default) { db.dispatchRecordsQueries.deleteOldDispatches(cutoff) }

    // --- Mahjong ---
    suspend fun getMahjongSave(): MahjongSave? = withContext(Dispatchers.Default) {
        db.mahjongSavesQueries.getSave().executeAsOneOrNull()?.let { MahjongSave(id = it.id, saveJson = it.saveJson, ruleType = it.ruleType, savedAt = it.savedAt) }
    }

    suspend fun saveMahjong(save: MahjongSave) = withContext(Dispatchers.Default) {
        db.mahjongSavesQueries.insertSave(save.id, save.saveJson, save.ruleType, save.savedAt)
    }

    suspend fun deleteMahjongSave() = withContext(Dispatchers.Default) { db.mahjongSavesQueries.deleteSave() }

    // --- Cleanup ---
    suspend fun cleanupExpiredData() = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        db.memoryAnchorsQueries.deleteExpiredAnchors(now)
        db.memoriesQueries.deleteExpired(now)
    }

    // --- Private chat context helpers ---
    suspend fun getPrivateChatSummary(operatorId: String): String? = withContext(Dispatchers.Default) {
        val session = db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull() ?: return@withContext null
        val recentMsgs = getMessagesSync(session.id).takeLast(5)
        if (recentMsgs.isEmpty()) return@withContext null
        recentMsgs.joinToString("\n") { "${if (it.isMe) "博士" else it.senderName}：${it.content.take(80)}" }
    }

    suspend fun getPrivateChatContext(operatorId: String): String? = withContext(Dispatchers.Default) {
        val session = db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull() ?: return@withContext null
        val impression = getLongTermImpression(operatorId)?.content?.take(100)
        val recentMsgs = getMessagesSync(session.id).takeLast(2)
        if (recentMsgs.isEmpty() && impression == null) return@withContext null
        val lines = mutableListOf<String>()
        if (impression != null) lines.add("印象：$impression")
        for (m in recentMsgs) {
            val name = if (m.isMe) "博士" else m.senderName
            val text = if (m.type == "ai_json") {
                try {
                    val obj = json.parseToJsonElement(m.content) as? kotlinx.serialization.json.JsonObject
                    val segs = obj?.get("segments") as? kotlinx.serialization.json.JsonArray
                    if (segs != null) {
                        segs.mapNotNull { seg ->
                            val segObj = seg as? kotlinx.serialization.json.JsonObject
                            if ((segObj?.get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content == "dialogue")
                                (segObj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            else null
                        }.joinToString(" ")
                    } else m.content.take(80)
                } catch (_: Exception) { m.content.take(80) }
            } else m.content.take(80)
            lines.add("$name：$text")
        }
        lines.joinToString("\n")
    }

    // --- Preset groups ---
    suspend fun initPresetGroups() = withContext(Dispatchers.Default) {
        val count = db.chatSessionsQueries.getGroupCount().executeAsOne().toInt()
        if (count > 0) return@withContext
        val now = System.currentTimeMillis()
        val groups = listOf(
            ChatSession(id = "group_elite", operatorId = "group_elite", operatorName = "罗德岛精英干员", lastMessage = "欢迎加入", members = "amiya,blaze,rosmontis,kaltsit,exusiai"),
            ChatSession(id = "group_logistics", operatorId = "group_logistics", operatorName = "企鹅物流", lastMessage = "欢迎加入", members = "exusiai,texas,angelina"),
            ChatSession(id = "group_medical", operatorId = "group_medical", operatorName = "医疗组", lastMessage = "欢迎加入", members = "kaltsit,nightingale,shining,ifrit,saria")
        )
        var msgId = 1L
        groups.forEach { g ->
            db.chatSessionsQueries.insertSession(g.id, g.operatorId, g.operatorName, g.lastMessage, now, g.mode, 0, 0, g.members, g.rules, g.avatarUri, g.mutedMembers)
            db.chatMessagesQueries.insertMessage(msgId++, g.id, "", "系统", "欢迎加入群聊", "system", "online", "", "", "", "", "", 0, now, 0)
        }
    }
}
