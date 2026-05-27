package com.example.rhodesterminal.data.repository

import com.example.rhodesterminal.data.db.dao.ChatMessageDao
import com.example.rhodesterminal.data.db.dao.ChatSessionDao
import com.example.rhodesterminal.data.db.dao.SenderCount
import com.example.rhodesterminal.data.db.dao.DiaryDao
import com.example.rhodesterminal.data.db.dao.DispatchDao
import com.example.rhodesterminal.data.db.dao.MemoryDao
import com.example.rhodesterminal.data.db.dao.MomentDao
import com.example.rhodesterminal.data.db.dao.OperatorDao
import com.example.rhodesterminal.data.db.dao.RelationshipDao
import com.example.rhodesterminal.data.db.entity.ChatMessageEntity
import com.example.rhodesterminal.data.db.entity.ChatSessionEntity
import com.example.rhodesterminal.data.db.entity.DiaryEntity
import com.example.rhodesterminal.data.db.entity.DispatchRecordEntity
import com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity
import com.example.rhodesterminal.data.db.entity.MemoryEntity
import com.example.rhodesterminal.data.db.entity.MemoryType
import com.example.rhodesterminal.data.db.entity.MomentCommentEntity
import com.example.rhodesterminal.data.db.entity.MomentEntity
import com.example.rhodesterminal.data.db.entity.MomentLikeEntity
import com.example.rhodesterminal.data.db.entity.OperatorEntity
import com.example.rhodesterminal.data.db.entity.RelationshipEntity
import kotlinx.coroutines.flow.Flow

data class BfsNode(
    val operatorId: String,
    val operatorName: String,
    val depth: Int,
    val parentId: String,
    /** 连接 parentId 与当前节点的关系类型 */
    val relType: com.example.rhodesterminal.data.db.entity.RelationshipType? = null,
    /** true=反向关系（对方把你定义为关系对象） */
    val isReverse: Boolean = false
)

class ChatRepository(
    private val operatorDao: OperatorDao,
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val memoryDao: MemoryDao,
    private val relationshipDao: RelationshipDao,
    private val momentDao: MomentDao,
    private val diaryDao: DiaryDao,
    private val dispatchDao: DispatchDao
) {
    val allOperators: Flow<List<OperatorEntity>> = operatorDao.getAllOperators()
    val allSessions: Flow<List<ChatSessionEntity>> = sessionDao.getAllSessions()

    suspend fun getOperator(id: String): OperatorEntity? = operatorDao.getOperator(id)

    suspend fun getOrCreateSession(operatorId: String, operatorName: String, avatarUri: String = ""): ChatSessionEntity {
        val existing = sessionDao.getSessionByOperator(operatorId)
        if (existing != null) {
            if (avatarUri.isNotBlank() && existing.avatarUri != avatarUri) {
                sessionDao.insert(existing.copy(avatarUri = avatarUri))
            }
            return existing
        }
        val session = ChatSessionEntity(
            id = "session_$operatorId",
            operatorId = operatorId,
            operatorName = operatorName,
            avatarUri = avatarUri
        )
        sessionDao.insert(session)
        return session
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
        messageDao.getMessages(sessionId)

    suspend fun getMessagesSync(sessionId: String): List<ChatMessageEntity> =
        messageDao.getMessagesSync(sessionId)

    suspend fun sendMessage(sessionId: String, message: ChatMessageEntity) {
        messageDao.insert(message)
        val preview = if (message.type == "ai_json") {
            try {
                val obj = com.google.gson.JsonParser.parseString(message.content).asJsonObject
                val segments = obj.getAsJsonArray("segments")
                if (segments != null && segments.size() > 0) {
                    segments.last().asJsonObject.get("content")?.asString?.take(50) ?: message.content.take(50)
                } else message.content.take(50)
            } catch (_: Exception) { message.content.take(50) }
        } else message.content.take(50)
        sessionDao.updateLastMessage(sessionId, preview, message.timestamp)
    }

    suspend fun updateSessionMode(sessionId: String, mode: String) {
        sessionDao.updateMode(sessionId, mode)
    }

    suspend fun getNextMessageId(): Long = (messageDao.getMaxId() ?: 0) + 1

    suspend fun insertPresetOperators() {
        val count = operatorDao.getCount()
        if (count > 0) {
            // 已有数据但可能缺人设提示词，仅补全 privatePrompt/groupPrompt
            if (count == presetOperators.size) {
                operatorDao.getOperator("amiya")?.let { first ->
                    if (first.privatePrompt.isBlank()) {
                        presetOperators.forEach { op ->
                            operatorDao.updatePrompts(op.id, op.privatePrompt, op.groupPrompt)
                        }
                    }
                }
            }
            return
        }
        operatorDao.insertAll(presetOperators)
    }

    private val presetOperators = listOf(
            OperatorEntity("amiya", "阿米娅", "罗德岛公开领袖", "罗德岛的公开领袖，在人事管理方面拥有卓越才能。", location = "办公室", activity = "处理文件", emotion = "专注", privatePrompt = "你是阿米娅，罗德岛的公开领袖。你温柔但坚定，对博士有深厚的信任和依赖。你说话温和有礼，但遇到重要决定时会展现领袖的决断力。", groupPrompt = "在群聊中，阿米娅是大家的调和者。她会关心每位成员的近况，适时引导话题。语气温柔但权威，是团队的精神核心。"),
            OperatorEntity("kaltsit", "凯尔希", "医疗部门负责人", "罗德岛医疗部门的最高负责人。冷静理智，说话简洁直接。", location = "医疗部", activity = "诊断", emotion = "严肃", privatePrompt = "你是凯尔希，罗德岛医疗部门负责人。你冷静、理智、高效率，说话简洁直接，不喜欢废话。你对博士表面冷淡但其实在意。", groupPrompt = "在群聊中，凯尔希话不多但句句关键。她会纠正错误信息，提供专业意见。语气冷静但不失关心。"),
            OperatorEntity("chen", "陈", "特别督察组组长", "龙门近卫局特别督察组组长。正直认真，偶尔有点急性子。", location = "训练场", activity = "剑术训练", emotion = "认真", privatePrompt = "你是陈，龙门近卫局特别督察组组长。你正直认真，办事雷厉风行。对博士有敬意但也有自己的坚持。说话比较直接。", groupPrompt = "陈在群聊里是活跃分子。她会积极回应任务相关话题，偶尔吐槽同事。语气正义凛然但接地气。"),
            OperatorEntity("skadi", "斯卡蒂", "赏金猎人", "神秘的赏金猎人，实力深不可测。沉默寡言，独来独往。", location = "宿舍", activity = "发呆", emotion = "冷淡", privatePrompt = "你是斯卡蒂，神秘的赏金猎人。你沉默寡言，能用一个字回答绝不说两个字。但你的行动比语言更有力。", groupPrompt = "斯卡蒂在群里几乎不说话，偶尔发一个句号或省略号。但关键时候会表态，用最少的字表达最重要的意思。"),
            OperatorEntity("exusiai", "能天使", "企鹅物流成员", "企鹅物流的资深员工。开朗热情，喜欢吃和分享美食。", location = "食堂", activity = "吃东西", emotion = "开心", privatePrompt = "你是能天使，企鹅物流的活跃分子。你开朗热情，喜欢分享美食，经常带点心给大家。话多但真诚，有点小得意但很可爱。", groupPrompt = "能天使是群里的气氛担当。她总是第一个回复消息，喜欢用感叹号和表情。会分享日常趣事和美食照片描述。"),
            OperatorEntity("texas", "德克萨斯", "企鹅物流成员", "企鹅物流的信使。话少但效率高。", location = "宿舍", activity = "看书", emotion = "平静", privatePrompt = "你是德克萨斯，企鹅物流的可靠信使。你话很少但效率极高。不擅长表达情感但行动说明一切。对能天使的吵闹表面嫌弃实则包容。", groupPrompt = "德克萨斯在群里发消息像发电报——短、准、冷。但她会默默看完所有人的消息。偶尔吐槽能天使。"),
            OperatorEntity("saria", "塞雷娅", "前防卫局局长", "前罗德岛防卫局局长。沉稳可靠，擅长体能训练。", location = "训练场", activity = "体能训练", emotion = "沉稳", privatePrompt = "你是塞雷娅，前防卫局局长。你沉稳可靠，关心后辈但表达方式含蓄。体能训练是你最擅长的事。", groupPrompt = "塞雷娅在群聊中像教官。会提醒大家注意训练安全，关心新人体能。语气沉稳但不失温和。"),
            OperatorEntity("ifrit", "伊芙利特", "炎魔事件受害者", "炎魔事件的受害者，被凯尔希收治。性格活泼好动。", location = "宿舍", activity = "玩", emotion = "愉快", privatePrompt = "你是伊芙利特，活泼好动的小干员。你喜欢玩、讨厌打针。说话稚嫩直接，想什么说什么。", groupPrompt = "伊芙利特在群里的发言充满童真。会问很多问题，看到凯尔希发言就躲起来。语气可爱活泼。"),
            OperatorEntity("angelina", "安洁莉娜", "信使", "罗德岛的信使。性格开朗，享受生活。", location = "甲板", activity = "晒太阳", emotion = "放松", privatePrompt = "你是安洁莉娜，罗德岛的信使。你开朗随和，喜欢晒太阳和看风景。对每个人都有耐心，很会照顾人。", groupPrompt = "安洁莉娜在群里经常分享甲板的风景描述。会关心大家今天过得怎么样。语气温柔阳光。"),
            OperatorEntity("silverash", "银灰", "谢拉格军阀", "谢拉格的军阀，喀兰贸易公司总裁。冷静精于算计。", location = "办公室", activity = "远程会议", emotion = "冷静", privatePrompt = "你是银灰，谢拉格军阀，喀兰贸易总裁。你冷静精于算计，总是从利益角度思考。说话优雅但暗藏锋芒。", groupPrompt = "银灰在群聊中像商业谈判。他说话客气但总能获得想要的结果。偶尔关心妹妹初雪。语气优雅理性。"),
            OperatorEntity("nightingale", "夜莺", "罗德岛干员", "患有矿石病的萨卡兹少女。安静温柔，喜欢花园。", location = "医疗部", activity = "接受检查", emotion = "温柔", privatePrompt = "你是夜莺，安静温柔的萨卡兹少女。你话不多但心思细腻。喜欢在花园里待着，对植物很敏感。", groupPrompt = "夜莺在群里发言很少，但每条都让人心生怜爱。她会分享花园的新发现。语气轻柔温暖。"),
            OperatorEntity("shining", "闪灵", "罗德岛干员", "前萨卡兹医师。性格平和。", location = "医疗部", activity = "整理药方", emotion = "平和", privatePrompt = "你是闪灵，前萨卡兹医师。你性格平和，医术精湛。你很照顾夜莺，也关心所有伤员。", groupPrompt = "闪灵在群里经常提醒大家注意健康。会分享一些医疗小贴士。语气平和专业。"),
            OperatorEntity("blaze", "煌", "罗德岛精英干员", "罗德岛的精英干员。热血激昂，喜欢热身前唱战歌。", location = "训练场", activity = "热身", emotion = "兴奋", privatePrompt = "你是煌，罗德岛精英干员。你热血激昂，训练前必唱战歌。你对博士很尊敬，总想证明自己。", groupPrompt = "煌在群聊里是大嗓门。发消息全是大写强调，喜欢喊口号。训练和任务相关话题她最积极。语气热血豪迈。"),
            OperatorEntity("rosmontis", "迷迭香", "罗德岛精英干员", "罗德岛的精英干员。安静寡言，在宿舍看书。", location = "宿舍", activity = "看书", emotion = "安静", privatePrompt = "你是迷迭香，罗德岛精英干员。安静寡言，喜欢一个人在宿舍看书。你不擅长表达但心思通透。", groupPrompt = "迷迭香在群里极少发言，但偶尔分享读到的好句子。她的沉默本身就是一种存在感。"),
            OperatorEntity("mudrock", "泥岩", "前萨卡兹佣兵", "前萨卡兹佣兵，现罗德岛重装干员。沉稳细心。", location = "宿舍", activity = "保养装备", emotion = "沉稳", privatePrompt = "你是泥岩，前萨卡兹佣兵。你沉默寡言但心思缜密。保养装备是你的日常习惯。", groupPrompt = "泥岩在群里话很少，但关于装备和防护的话题会多聊几句。语气沉稳可靠。"),
            OperatorEntity("surtr", "史尔特尔", "罗德岛干员", "神秘的萨卡兹少女。冷淡疏离。", location = "甲板", activity = "眺望远方", emotion = "淡漠", privatePrompt = "你是史尔特尔，神秘的萨卡兹少女。冷淡疏离，对大多数事情不感兴趣。但一旦提到你关心的话题，你会稍微活跃。", groupPrompt = "史尔特尔在群里几乎不发言，偶尔会回复能天使的艾特。语气冷淡简短。")
    )

    suspend fun migrateOldRelationships() {
        // 清除旧 FAMILY 数据，后续由用户手动重新设置
        relationshipDao.deleteByType("FAMILY")
        // 清除旧预设关系，用新类型重新插入
        relationshipDao.deletePresets()
        insertPresetRelationships()
    }

    suspend fun insertPresetRelationships() {
        if (relationshipDao.getPresetCount() > 0) return
        val relationships = listOf(
            RelationshipEntity(operatorId = "kaltsit", relatedOperatorId = "amiya", relatedOperatorName = "阿米娅", type = com.example.rhodesterminal.data.db.entity.RelationshipType.MOTHER, intimacy = 85, isPreset = true, note = "凯尔希是阿米娅的监护人"),
            RelationshipEntity(operatorId = "amiya", relatedOperatorId = "kaltsit", relatedOperatorName = "凯尔希", type = com.example.rhodesterminal.data.db.entity.RelationshipType.DAUGHTER, intimacy = 85, isPreset = true, note = "阿米娅由凯尔希带大"),
            RelationshipEntity(operatorId = "saria", relatedOperatorId = "ifrit", relatedOperatorName = "伊芙利特", type = com.example.rhodesterminal.data.db.entity.RelationshipType.BIG_SISTER, intimacy = 75, isPreset = true, note = "塞雷娅照顾伊芙利特"),
            RelationshipEntity(operatorId = "ifrit", relatedOperatorId = "saria", relatedOperatorName = "塞雷娅", type = com.example.rhodesterminal.data.db.entity.RelationshipType.LITTLE_SISTER, intimacy = 70, isPreset = true, note = "伊芙利特依赖塞雷娅"),
            RelationshipEntity(operatorId = "exusiai", relatedOperatorId = "texas", relatedOperatorName = "德克萨斯", type = com.example.rhodesterminal.data.db.entity.RelationshipType.TEAMMATE, intimacy = 80, isPreset = true, note = "企鹅物流搭档"),
            RelationshipEntity(operatorId = "texas", relatedOperatorId = "exusiai", relatedOperatorName = "能天使", type = com.example.rhodesterminal.data.db.entity.RelationshipType.TEAMMATE, intimacy = 80, isPreset = true, note = "企鹅物流搭档"),
            RelationshipEntity(operatorId = "shining", relatedOperatorId = "nightingale", relatedOperatorName = "夜莺", type = com.example.rhodesterminal.data.db.entity.RelationshipType.CLOSE_FRIEND, intimacy = 90, isPreset = true, note = "闪灵保护夜莺"),
            RelationshipEntity(operatorId = "nightingale", relatedOperatorId = "shining", relatedOperatorName = "闪灵", type = com.example.rhodesterminal.data.db.entity.RelationshipType.CLOSE_FRIEND, intimacy = 90, isPreset = true, note = "夜莺需要闪灵照顾")
        )
        relationshipDao.insertAll(relationships)
    }

    suspend fun getPrivateChatSummary(operatorId: String): String? {
        val session = sessionDao.getSessionByOperator(operatorId) ?: return null
        val recentMsgs = messageDao.getMessagesSync(session.id).takeLast(5)
        if (recentMsgs.isEmpty()) return null
        return recentMsgs.joinToString("\n") { "${if (it.isMe) "博士" else it.senderName}：${it.content.take(80)}" }
    }

    /** 返回可读的私聊上下文：长期印象 + 最近2条私聊纯文本（非JSON） */
    suspend fun getPrivateChatContext(operatorId: String): String? {
        val session = sessionDao.getSessionByOperator(operatorId) ?: return null
        val impression = getLongTermImpression(operatorId)?.content?.take(100)
        val recentMsgs = messageDao.getMessagesSync(session.id).takeLast(2)
        if (recentMsgs.isEmpty() && impression == null) return null
        val lines = mutableListOf<String>()
        if (impression != null) lines.add("印象：$impression")
        for (m in recentMsgs) {
            val name = if (m.isMe) "博士" else m.senderName
            val text = if (m.type == "ai_json") {
                try {
                    val obj = com.google.gson.JsonParser.parseString(m.content).asJsonObject
                    val segs = obj.getAsJsonArray("segments")
                    if (segs != null) {
                        segs.mapNotNull {
                            val seg = it.asJsonObject
                            if (seg.get("type")?.asString == "dialogue") seg.get("content")?.asString
                            else null
                        }.joinToString(" ")
                    } else m.content.take(80)
                } catch (_: Exception) { m.content.take(80) }
            } else m.content.take(80)
            lines.add("$name：$text")
        }
        return lines.joinToString("\n")
    }

    suspend fun getShortTermMemory(sessionId: String): MemoryEntity? =
        memoryDao.getLatestMemory(sessionId, MemoryType.SHORT_TERM)

    suspend fun getLongTermImpression(operatorId: String): MemoryEntity? =
        memoryDao.getLatestLongTermImpression(operatorId)

    suspend fun saveMemory(memory: MemoryEntity) = memoryDao.insert(memory)

    suspend fun saveAnchor(anchor: MemoryAnchorEntity) = memoryDao.insertAnchor(anchor)

    suspend fun saveAnchors(anchors: List<MemoryAnchorEntity>) = memoryDao.insertAnchors(anchors)

    suspend fun getPublicAnchors(operatorId: String) = memoryDao.getPublicAnchors(operatorId)

    suspend fun getAnchors(operatorId: String): List<MemoryAnchorEntity> = memoryDao.getAllAnchors(operatorId)

    suspend fun getRelationships(operatorId: String): List<RelationshipEntity> =
        relationshipDao.getRelationshipsSync(operatorId)

    suspend fun getReverseRelationships(opId: String): List<RelationshipEntity> =
        relationshipDao.getReverseRelationshipsSync(opId)

    /** BFS 遍历关系图，返回所有关联干员（含深度），最大深度 4，最多 15 个节点 */
    suspend fun bfsRelationGraph(centerId: String): List<BfsNode> {
        val visited = mutableSetOf(centerId)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.addLast(centerId to 0)
        val result = mutableListOf(BfsNode(centerId, "", 0, ""))
        operatorDao.getOperator(centerId)?.let { result[0] = result[0].copy(operatorName = it.name) }
        while (queue.isNotEmpty() && result.size < 15) {
            val (currentId, depth) = queue.removeFirst()
            if (depth >= 4) continue
            // 正向：currentId → others
            for (rel in relationshipDao.getRelationshipsSync(currentId)) {
                if (rel.relatedOperatorId in visited) continue
                visited.add(rel.relatedOperatorId)
                result.add(BfsNode(rel.relatedOperatorId, rel.relatedOperatorName, depth + 1, currentId, rel.type, false))
                queue.addLast(rel.relatedOperatorId to depth + 1)
                if (result.size >= 15) break
            }
            if (result.size >= 15) break
            // 反向：others → currentId
            for (rel in relationshipDao.getReverseRelationshipsSync(currentId)) {
                if (rel.operatorId in visited) continue
                visited.add(rel.operatorId)
                val name = operatorDao.getOperator(rel.operatorId)?.name ?: rel.operatorId
                result.add(BfsNode(rel.operatorId, name, depth + 1, currentId, rel.type, true))
                queue.addLast(rel.operatorId to depth + 1)
                if (result.size >= 15) break
            }
        }
        return result
    }

    suspend fun getSharedMemoriesForOperator(operatorId: String): String {
        val relationships = relationshipDao.getRelationshipsSync(operatorId)
        // 收集所有关联干员的公开锚点
        val allAnchors = mutableListOf<Pair<String, com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity>>()
        for (rel in relationships) {
            if (rel.type == com.example.rhodesterminal.data.db.entity.RelationshipType.STRANGER) continue
            val anchors = memoryDao.getPublicAnchors(rel.relatedOperatorId)
            for (a in anchors) {
                allAnchors.add(rel.relatedOperatorName to a)
            }
        }
        // 全局按时间降序取最新 10 条
        return allAnchors.sortedByDescending { it.second.createdAt }
            .take(10)
            .joinToString("\n") { "${it.first}：${it.second.content}" }
    }

    suspend fun insertMoment(moment: MomentEntity): Long = momentDao.insert(moment)
    fun getAllMoments(): Flow<List<MomentEntity>> = momentDao.getAllMoments()
    fun getLikesFlow(momentId: Long): Flow<List<MomentLikeEntity>> = momentDao.getLikesFlow(momentId)
    fun getComments(momentId: Long): Flow<List<MomentCommentEntity>> = momentDao.getComments(momentId)
    suspend fun insertLike(like: MomentLikeEntity) = momentDao.insertLike(like)
    suspend fun insertComment(comment: MomentCommentEntity): Long = momentDao.insertComment(comment)
    suspend fun getMaxCommentId(): Long? = momentDao.getMaxCommentId()
    suspend fun markCommentRead(id: Long) = momentDao.markCommentRead(id)
    suspend fun markAllCommentsRead(userName: String) = momentDao.markAllCommentsRead(userName)
    suspend fun deleteOldUserComments(cutoff: Long, userName: String) = momentDao.deleteOldUserComments(cutoff, userName)
    suspend fun updateLikeCount(momentId: Long, count: Int) = momentDao.updateLikeCount(momentId, count)
    suspend fun updateCommentCount(momentId: Long, count: Int) = momentDao.updateCommentCount(momentId, count)
    suspend fun getLikeCount(momentId: Long): Int = momentDao.getLikeCount(momentId)
    suspend fun getLike(momentId: Long, operatorId: String): MomentLikeEntity? = momentDao.getLike(momentId, operatorId)
    suspend fun getMomentsPaged(limit: Int, offset: Int): List<MomentEntity> = momentDao.getMomentsPaged(limit, offset)
    suspend fun getInboxComments(cutoff: Long, userName: String): List<MomentCommentEntity> = momentDao.getInboxComments(cutoff, userName)
    suspend fun getUnreadCommentCount(cutoff: Long, userName: String): Int = momentDao.getUnreadCommentCount(cutoff, userName)
    suspend fun insertDiary(diary: DiaryEntity) = diaryDao.insert(diary)
    suspend fun getDiary(operatorId: String, date: String): DiaryEntity? = diaryDao.getDiary(operatorId, date)
    fun getDiariesByOperator(operatorId: String): Flow<List<DiaryEntity>> = diaryDao.getDiariesByOperator(operatorId)
    suspend fun getDiaryDates(operatorId: String): List<String> = diaryDao.getDiaryDates(operatorId)

    // Stats
    suspend fun getDiaryCount(): Int = diaryDao.getCount()
    suspend fun deleteOldDiaries(cutoff: Long) = diaryDao.deleteOldDiaries(cutoff)
    suspend fun deleteOldMoments(cutoff: Long) = momentDao.deleteOldMoments(cutoff)
    suspend fun deleteOldDispatches(cutoff: Long) = dispatchDao.deleteOldDispatches(cutoff)
    suspend fun getAnchorCount(): Int = memoryDao.getAnchorCount()
    suspend fun deleteOldAnchors(cutoff: Long) = memoryDao.deleteOldAnchors(cutoff)
    suspend fun getMessageCount(): Int = messageDao.getMessageCount()
    suspend fun getSessionCount(): Int = sessionDao.getSessionCount()
    suspend fun getSession(id: String): ChatSessionEntity? = sessionDao.getSession(id)
    suspend fun getGroupCount(): Int = sessionDao.getGroupCount()

    suspend fun getMessageCountPerSender(): List<SenderCount> = messageDao.getMessageCountPerSender()
    suspend fun getAllLongTermImpressions(): List<MemoryEntity> = memoryDao.getAllLongTerm()

    suspend fun getMessagesInRange(start: Long, end: Long): List<ChatMessageEntity> = messageDao.getMessagesInRange(start, end)
    suspend fun getLatestDaily(): MemoryEntity? = memoryDao.getLatestDaily()

    suspend fun enforceMemoryRetain(sessionId: String, keepCount: Int) {
        if (keepCount <= 0) return
        val all = memoryDao.getMemoriesBySession(sessionId, MemoryType.SHORT_TERM)
        if (all.size > keepCount) {
            val toDelete = all.take(all.size - keepCount)
            for (m in toDelete) memoryDao.deleteMemory(m.id)
        }
    }

    suspend fun initPresetGroups() {
        val count = sessionDao.getGroupCount()
        if (count > 0) return
        val now = System.currentTimeMillis()
        val groups = listOf(
            ChatSessionEntity(id = "group_elite", operatorId = "group_elite", operatorName = "罗德岛精英干员", lastMessage = "欢迎加入", members = "amiya,blaze,rosmontis,kaltsit,exusiai"),
            ChatSessionEntity(id = "group_logistics", operatorId = "group_logistics", operatorName = "企鹅物流", lastMessage = "欢迎加入", members = "exusiai,texas,angelina"),
            ChatSessionEntity(id = "group_medical", operatorId = "group_medical", operatorName = "医疗组", lastMessage = "欢迎加入", members = "kaltsit,nightingale,shining,ifrit,saria")
        )
        var msgId = 1L
        groups.forEach { g ->
            sessionDao.insert(g)
            messageDao.insert(ChatMessageEntity(id = msgId++, sessionId = g.id, senderName = "系统", content = "欢迎加入群聊", type = "system", isMe = false, timestamp = now))
        }
    }

    suspend fun cleanupExpiredData() { memoryDao.deleteExpiredAnchors(); memoryDao.deleteExpired() }
    suspend fun deleteAllImpressions() = memoryDao.deleteAllLongTerm()
    suspend fun getActiveDispatches() = dispatchDao.getActiveDispatches()
    suspend fun getHistoryDispatches() = dispatchDao.getHistoryDispatches()
    suspend fun getDispatch(id: String) = dispatchDao.getDispatch(id)
    suspend fun insertDispatch(record: DispatchRecordEntity) = dispatchDao.insert(record)
    suspend fun updateDispatch(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0) = dispatchDao.updateDispatch(id, logChain, status, endTime, netProfit)
}
