package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OperatorRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Operators ---
    val allOperators: Flow<List<Operator>> =
        db.operatorsQueries.getAllOperators { id, name, title, description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt, userRelation, lmb, attack, defense, meldPref ->
            Operator(id, name, title, description, avatarUri, location, activity, emotion, intimacy.toInt(), privatePrompt, groupPrompt, userRelation, lmb.toInt(), attack.toFloat(), defense.toFloat(), meldPref)
        }.asFlow().mapToList(Dispatchers.Default)

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
}
