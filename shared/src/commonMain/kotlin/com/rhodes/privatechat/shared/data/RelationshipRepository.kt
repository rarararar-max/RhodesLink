package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class RelationshipRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Relationships ---
    suspend fun migrateOldRelationships() = withContext(Dispatchers.Default) {
        db.relationshipsQueries.deleteByType("FAMILY")
        db.relationshipsQueries.deletePresets()
        insertPresetRelationships()
    }

    suspend fun insertPresetRelationships() = withContext(Dispatchers.Default) {
        // 已清空预设关系，让用户自己建立
        return@withContext
        val relationships = listOf(
            // === 岁家 ===
            Relationship(id = 0, "chongyue", "shu", "黍", RelationshipType.BIG_BROTHER, 80, isPreset = true, note = "重岳是岁家大哥"),
            Relationship(id = 0, "shu", "chongyue", "重岳", RelationshipType.LITTLE_SISTER, 80, isPreset = true, note = "黍尊敬大哥"),
            Relationship(id = 0, "chongyue", "wang", "望", RelationshipType.BIG_BROTHER, 75, isPreset = true, note = "岁家大哥与二哥"),
            Relationship(id = 0, "wang", "chongyue", "重岳", RelationshipType.LITTLE_BROTHER, 70, isPreset = true, note = "望尊敬大哥"),
            Relationship(id = 0, "chongyue", "yu", "余", RelationshipType.BIG_BROTHER, 85, isPreset = true, note = "大哥疼爱幺弟"),
            Relationship(id = 0, "yu", "chongyue", "重岳", RelationshipType.LITTLE_BROTHER, 85, isPreset = true, note = "余崇拜大哥"),
            Relationship(id = 0, "wang", "jie", "颉", RelationshipType.BIG_BROTHER, 90, isPreset = true, note = "望对三妹的愧疚"),
            Relationship(id = 0, "jie", "wang", "望", RelationshipType.LITTLE_SISTER, 90, isPreset = true, note = "颉理解二哥"),
            Relationship(id = 0, "shu", "nian", "年", RelationshipType.BIG_SISTER, 75, isPreset = true, note = "黍照顾年"),
            Relationship(id = 0, "nian", "shu", "黍", RelationshipType.LITTLE_SISTER, 75, isPreset = true, note = "年依赖黍姐"),
            Relationship(id = 0, "nian", "dusk", "夕", RelationshipType.BIG_SISTER, 65, isPreset = true, note = "年是夕的姐姐"),
            Relationship(id = 0, "dusk", "nian", "年", RelationshipType.LITTLE_SISTER, 65, isPreset = true, note = "夕与年的姐妹情"),
            Relationship(id = 0, "ling", "nian", "年", RelationshipType.BIG_SISTER, 70, isPreset = true, note = "令关心年"),
            Relationship(id = 0, "nian", "ling", "令", RelationshipType.LITTLE_SISTER, 70, isPreset = true, note = "年敬爱令姐"),
            Relationship(id = 0, "ling", "yu", "余", RelationshipType.FRIEND, 65, isPreset = true, note = "令与余投缘"),
            Relationship(id = 0, "yu", "ling", "令", RelationshipType.FRIEND, 65, isPreset = true, note = "余喜欢和令姐喝酒"),
            // === 深海猎人 ===
            Relationship(id = 0, "skadi", "gladiia", "歌蕾蒂娅", RelationshipType.TEAMMATE, 75, isPreset = true, note = "深海猎人同僚"),
            Relationship(id = 0, "gladiia", "skadi", "斯卡蒂", RelationshipType.TEAMMATE, 75, isPreset = true, note = "深海猎人指挥官"),
            Relationship(id = 0, "skadi", "specter", "幽灵鲨", RelationshipType.TEAMMATE, 70, isPreset = true, note = "深海猎人同伴"),
            Relationship(id = 0, "specter", "skadi", "斯卡蒂", RelationshipType.TEAMMATE, 70, isPreset = true, note = "深海猎人同伴"),
            Relationship(id = 0, "gladiia", "specter", "幽灵鲨", RelationshipType.TEAMMATE, 70, isPreset = true, note = "深海猎人指挥官"),
            Relationship(id = 0, "specter", "gladiia", "歌蕾蒂娅", RelationshipType.TEAMMATE, 70, isPreset = true, note = "幽灵鲨尊重指挥"),
            Relationship(id = 0, "skadi", "ulpianus", "乌尔比安", RelationshipType.TEAMMATE, 65, isPreset = true, note = "深海猎人前后辈"),
            Relationship(id = 0, "ulpianus", "skadi", "斯卡蒂", RelationshipType.TEAMMATE, 65, isPreset = true, note = "首位猎人与后辈"),
            Relationship(id = 0, "gladiia", "ulpianus", "乌尔比安", RelationshipType.TEAMMATE, 70, isPreset = true, note = "深海猎人同僚"),
            Relationship(id = 0, "ulpianus", "gladiia", "歌蕾蒂娅", RelationshipType.TEAMMATE, 70, isPreset = true, note = "深海猎人同事"),
            Relationship(id = 0, "mizuki", "skadi", "斯卡蒂", RelationshipType.CLOSE_FRIEND, 50, isPreset = true, note = "与深海相关的共鸣"),
            // === 莱茵生命 ===
            Relationship(id = 0, "saria", "ifrit", "伊芙利特", RelationshipType.GUARDIAN, 75, isPreset = true, note = "塞雷娅守护伊芙利特"),
            Relationship(id = 0, "ifrit", "saria", "塞雷娅", RelationshipType.LITTLE_SISTER, 70, isPreset = true, note = "伊芙利特视塞雷娅如姐姐"),
            Relationship(id = 0, "silence", "ifrit", "伊芙利特", RelationshipType.GUARDIAN, 80, isPreset = true, note = "赫默照顾伊芙利特"),
            Relationship(id = 0, "ifrit", "silence", "赫默", RelationshipType.CLOSE_FRIEND, 75, isPreset = true, note = "伊芙利特依赖赫默"),
            Relationship(id = 0, "saria", "muelsyse", "缪尔赛思", RelationshipType.FRIEND, 70, isPreset = true, note = "莱茵生命前同事"),
            Relationship(id = 0, "muelsyse", "saria", "塞雷娅", RelationshipType.FRIEND, 70, isPreset = true, note = "缪尔赛思关心塞雷娅"),
            Relationship(id = 0, "saria", "silence", "赫默", RelationshipType.TEAMMATE, 65, isPreset = true, note = "莱茵前同事"),
            Relationship(id = 0, "silence", "saria", "塞雷娅", RelationshipType.TEAMMATE, 65, isPreset = true, note = "赫默与塞雷娅"),
            Relationship(id = 0, "ptilopsis", "whitesmith", "Whitesmith", RelationshipType.TEAMMATE, 60, isPreset = true, note = "莱茵技术专家"),
            Relationship(id = 0, "whitesmith", "ptilopsis", "白面鸮", RelationshipType.TEAMMATE, 60, isPreset = true, note = "莱茵技术同僚"),
            Relationship(id = 0, "nasti", "saria", "塞雷娅", RelationshipType.TEAMMATE, 60, isPreset = true, note = "莱茵工程科同事"),
            Relationship(id = 0, "saria", "nasti", "娜斯提", RelationshipType.TEAMMATE, 60, isPreset = true, note = "防卫科与工程科"),
            Relationship(id = 0, "dorothy", "muelsyse", "缪尔赛思", RelationshipType.FRIEND, 55, isPreset = true, note = "莱茵前同事"),
            // === 拉特兰 ===
            Relationship(id = 0, "executor", "arturia", "阿尔图罗", RelationshipType.FAMILY, 60, isPreset = true, note = "送葬人与阿尔图罗的血缘"),
            Relationship(id = 0, "arturia", "executor", "送葬人", RelationshipType.FAMILY, 60, isPreset = true, note = "阿尔图罗与送葬人"),
            Relationship(id = 0, "exusiai", "mostima", "莫斯提马", RelationshipType.CLOSE_FRIEND, 70, isPreset = true, note = "能天使与莫斯提马的友谊"),
            Relationship(id = 0, "mostima", "exusiai", "能天使", RelationshipType.CLOSE_FRIEND, 70, isPreset = true, note = "莫斯提马与能天使的羁绊"),
            Relationship(id = 0, "exusiai", "lemuen", "蕾缪安", RelationshipType.FAMILY, 75, isPreset = true, note = "能天使与姐姐蕾缪安"),
            Relationship(id = 0, "lemuen", "exusiai", "能天使", RelationshipType.FAMILY, 75, isPreset = true, note = "蕾缪安关心妹妹"),
            Relationship(id = 0, "fiammetta", "mostima", "莫斯提马", RelationshipType.GUARDIAN, 60, isPreset = true, note = "菲亚梅塔监视莫斯提马"),
            Relationship(id = 0, "archetto", "executor", "送葬人", RelationshipType.FRIEND, 55, isPreset = true, note = "兰登修道院的渊源"),
            Relationship(id = 0, "executor", "archetto", "空弦", RelationshipType.FRIEND, 55, isPreset = true, note = "送葬人与空弦相识"),
            // === 谢拉格 ===
            Relationship(id = 0, "silverash", "pramanix", "初雪", RelationshipType.BIG_BROTHER, 75, isPreset = true, note = "银灰关心妹妹"),
            Relationship(id = 0, "pramanix", "silverash", "银灰", RelationshipType.LITTLE_SISTER, 70, isPreset = true, note = "初雪对哥哥的复杂感情"),
            Relationship(id = 0, "silverash", "jian", "锏", RelationshipType.BOSS, 75, isPreset = true, note = "银灰雇佣锏"),
            Relationship(id = 0, "jian", "silverash", "银灰", RelationshipType.SUBORDINATE, 75, isPreset = true, note = "锏效忠于银灰"),
            // === 卡西米尔 ===
            Relationship(id = 0, "nearl", "blemishine", "瑕光", RelationshipType.BIG_SISTER, 85, isPreset = true, note = "临光是瑕光的姐姐"),
            Relationship(id = 0, "blemishine", "nearl", "临光", RelationshipType.LITTLE_SISTER, 85, isPreset = true, note = "瑕光崇拜姐姐"),
            Relationship(id = 0, "nearl", "mlynar", "玛恩纳", RelationshipType.FAMILY, 65, isPreset = true, note = "临光与叔父玛恩纳"),
            Relationship(id = 0, "mlynar", "nearl", "临光", RelationshipType.FAMILY, 65, isPreset = true, note = "玛恩纳守护临光家"),
            Relationship(id = 0, "whiplash", "nearl", "临光", RelationshipType.FAMILY, 70, isPreset = true, note = "鞭刃是临光的姑母"),
            Relationship(id = 0, "nearl", "whiplash", "鞭刃佐菲娅", RelationshipType.FAMILY, 70, isPreset = true, note = "临光尊敬姑母"),
            Relationship(id = 0, "whiplash", "blemishine", "瑕光", RelationshipType.FAMILY, 75, isPreset = true, note = "鞭刃训练瑕光"),
            Relationship(id = 0, "blemishine", "whiplash", "鞭刃佐菲娅", RelationshipType.FAMILY, 75, isPreset = true, note = "瑕光亲近姑母"),
            Relationship(id = 0, "viviana", "nearl", "临光", RelationshipType.FRIEND, 60, isPreset = true, note = "烛骑士经耀骑士举荐"),
            Relationship(id = 0, "nearl", "viviana", "薇薇安娜", RelationshipType.FRIEND, 60, isPreset = true, note = "临光欣赏薇薇安娜"),
            // === 企鹅物流 ===
            Relationship(id = 0, "exusiai", "texas", "德克萨斯", RelationshipType.TEAMMATE, 80, isPreset = true, note = "企鹅物流黄金搭档"),
            Relationship(id = 0, "texas", "exusiai", "能天使", RelationshipType.TEAMMATE, 80, isPreset = true, note = "企鹅物流搭档"),
            Relationship(id = 0, "exusiai", "croissant", "可颂", RelationshipType.TEAMMATE, 75, isPreset = true, note = "企鹅物流同事"),
            Relationship(id = 0, "croissant", "exusiai", "能天使", RelationshipType.TEAMMATE, 75, isPreset = true, note = "企鹅物流同事"),
            Relationship(id = 0, "texas", "croissant", "可颂", RelationshipType.TEAMMATE, 70, isPreset = true, note = "企鹅物流搭档"),
            Relationship(id = 0, "croissant", "texas", "德克萨斯", RelationshipType.TEAMMATE, 70, isPreset = true, note = "企鹅物流伙伴"),
            Relationship(id = 0, "sora", "texas", "德克萨斯", RelationshipType.CLOSE_FRIEND, 80, isPreset = true, note = "空被德克萨斯所救"),
            Relationship(id = 0, "texas", "sora", "空", RelationshipType.CLOSE_FRIEND, 75, isPreset = true, note = "德克萨斯保护空"),
            // === 龙门 ===
            Relationship(id = 0, "chen", "hoshiguma", "星熊", RelationshipType.TEAMMATE, 85, isPreset = true, note = "龙门近卫局最佳搭档"),
            Relationship(id = 0, "hoshiguma", "chen", "陈", RelationshipType.TEAMMATE, 85, isPreset = true, note = "龙门近卫局最佳搭档"),
            Relationship(id = 0, "chen", "waaifu", "槐琥", RelationshipType.TEAMMATE, 70, isPreset = true, note = "龙门近卫局同事"),
            Relationship(id = 0, "waaifu", "chen", "陈", RelationshipType.TEAMMATE, 70, isPreset = true, note = "龙门近卫局同事"),
            Relationship(id = 0, "chen", "swire", "诗怀雅", RelationshipType.RIVAL, 60, isPreset = true, note = "龙门近卫局亦敌亦友"),
            Relationship(id = 0, "swire", "chen", "陈", RelationshipType.RIVAL, 60, isPreset = true, note = "诗怀雅与陈的竞争"),
            Relationship(id = 0, "hoshiguma", "waaifu", "槐琥", RelationshipType.TEAMMATE, 65, isPreset = true, note = "龙门近卫局同僚"),
            Relationship(id = 0, "waaifu", "hoshiguma", "星熊", RelationshipType.TEAMMATE, 65, isPreset = true, note = "槐琥与星熊共事"),
            Relationship(id = 0, "lin", "chen", "陈", RelationshipType.FRIEND, 65, isPreset = true, note = "林与陈的合作"),
            Relationship(id = 0, "shirayuki", "chen", "陈", RelationshipType.SUBORDINATE, 70, isPreset = true, note = "白雪协助陈"),
            Relationship(id = 0, "jaye", "waaifu", "槐琥", RelationshipType.FRIEND, 55, isPreset = true, note = "龙门旧识"),
            Relationship(id = 0, "waaifu", "jaye", "孑", RelationshipType.FRIEND, 55, isPreset = true, note = "槐琥认识孑"),
            // === 维多利亚 ===
            Relationship(id = 0, "siege", "bagpipe", "风笛", RelationshipType.COMRADE, 65, isPreset = true, note = "维多利亚的战友"),
            Relationship(id = 0, "bagpipe", "siege", "推进之王", RelationshipType.COMRADE, 65, isPreset = true, note = "风笛与维娜并肩"),
            Relationship(id = 0, "siege", "horn", "号角", RelationshipType.COMRADE, 60, isPreset = true, note = "维多利亚盟友"),
            Relationship(id = 0, "horn", "siege", "推进之王", RelationshipType.COMRADE, 60, isPreset = true, note = "号角与格拉斯哥帮合作"),
            Relationship(id = 0, "reed2", "eblana", "死芒", RelationshipType.LITTLE_SISTER, 80, isPreset = true, note = "焰影苇草是死芒的妹妹"),
            Relationship(id = 0, "eblana", "reed2", "焰影苇草", RelationshipType.BIG_SISTER, 80, isPreset = true, note = "死芒关心妹妹"),
            Relationship(id = 0, "horn", "bagpipe", "风笛", RelationshipType.COMRADE, 65, isPreset = true, note = "号角与风笛的战友之情"),
            Relationship(id = 0, "bagpipe", "horn", "号角", RelationshipType.COMRADE, 65, isPreset = true, note = "风笛信任号角"),
            // === 萨卡兹 / 巴别塔 ===
            Relationship(id = 0, "w", "theresa", "特蕾西娅", RelationshipType.CLOSE_FRIEND, 90, isPreset = true, note = "W对特蕾西娅的忠诚"),
            Relationship(id = 0, "theresa", "w", "维什戴尔", RelationshipType.CLOSE_FRIEND, 80, isPreset = true, note = "特蕾西娅信任W"),
            Relationship(id = 0, "logos", "lalamen", "菈玛莲", RelationshipType.SON, 85, isPreset = true, note = "逻各斯是菈玛莲的儿子"),
            Relationship(id = 0, "lalamen", "logos", "逻各斯", RelationshipType.MOTHER, 85, isPreset = true, note = "菈玛莲牵挂儿子"),
            Relationship(id = 0, "scout", "w", "维什戴尔", RelationshipType.COMRADE, 70, isPreset = true, note = "巴别塔时期的战友"),
            Relationship(id = 0, "w", "scout", "Scout", RelationshipType.COMRADE, 70, isPreset = true, note = "W与Scout曾并肩"),
            Relationship(id = 0, "ascalon", "kaltsit", "凯尔希", RelationshipType.SUBORDINATE, 85, isPreset = true, note = "阿斯卡纶直属凯尔希"),
            Relationship(id = 0, "kaltsit", "ascalon", "阿斯卡纶", RelationshipType.BOSS, 85, isPreset = true, note = "凯尔希指挥阿斯卡纶"),
            Relationship(id = 0, "misery", "logos", "逻各斯", RelationshipType.TEAMMATE, 65, isPreset = true, note = "罗德岛精英干员"),
            Relationship(id = 0, "logos", "misery", "Misery", RelationshipType.TEAMMATE, 65, isPreset = true, note = "精英干员的信任"),
            Relationship(id = 0, "entelecheia", "w", "维什戴尔", RelationshipType.COMRADE, 55, isPreset = true, note = "玫瑰河畔的渊源"),
            // === 罗德岛核心 ===
            Relationship(id = 0, "amiya", "kaltsit", "凯尔希", RelationshipType.DAUGHTER, 85, isPreset = true, note = "阿米娅视凯尔希如母亲"),
            Relationship(id = 0, "kaltsit", "amiya", "阿米娅", RelationshipType.MOTHER, 85, isPreset = true, note = "凯尔希守护阿米娅"),
            Relationship(id = 0, "amiya", "logos", "逻各斯", RelationshipType.STUDENT, 70, isPreset = true, note = "阿米娅向逻各斯学习"),
            Relationship(id = 0, "logos", "amiya", "阿米娅", RelationshipType.MENTOR, 70, isPreset = true, note = "逻各斯引导阿米娅"),
            Relationship(id = 0, "doctor", "amiya", "阿米娅", RelationshipType.FAMILY, 80, isPreset = true, note = "博士与阿米娅如家人"),
            Relationship(id = 0, "amiya", "doctor", "博士", RelationshipType.FAMILY, 80, isPreset = true, note = "阿米娅信任博士"),
            Relationship(id = 0, "closure", "kaltsit", "凯尔希", RelationshipType.FRIEND, 55, isPreset = true, note = "可露希尔与凯尔希"),
            Relationship(id = 0, "warfarin", "kaltsit", "凯尔希", RelationshipType.TEAMMATE, 60, isPreset = true, note = "华法琳与凯尔希共事"),
            Relationship(id = 0, "dobermann", "amiya", "阿米娅", RelationshipType.MENTOR, 65, isPreset = true, note = "杜宾训练阿米娅"),
            // === 行动预备组 ===
            Relationship(id = 0, "melantha", "cardigan", "卡缇", RelationshipType.CAPTAIN, 65, isPreset = true, note = "玫兰莎是A4队长"),
            Relationship(id = 0, "cardigan", "melantha", "玫兰莎", RelationshipType.MEMBER, 65, isPreset = true, note = "卡缇是A4队员"),
            Relationship(id = 0, "melantha", "ansel", "安赛尔", RelationshipType.TEAMMATE, 60, isPreset = true, note = "A4小队同伴"),
            Relationship(id = 0, "ansel", "melantha", "玫兰莎", RelationshipType.TEAMMATE, 60, isPreset = true, note = "A4医疗与队长"),
            Relationship(id = 0, "cardigan", "ansel", "安赛尔", RelationshipType.TEAMMATE, 55, isPreset = true, note = "A4小队同伴"),
            Relationship(id = 0, "ansel", "cardigan", "卡缇", RelationshipType.TEAMMATE, 55, isPreset = true, note = "A4小队同伴"),
            Relationship(id = 0, "fang", "melantha", "玫兰莎", RelationshipType.FRIEND, 50, isPreset = true, note = "预备组队长交流"),
            // === 叙拉古 ===
            Relationship(id = 0, "lappland", "texas", "德克萨斯", RelationshipType.RIVAL, 80, isPreset = true, note = "拉普兰德的执念"),
            Relationship(id = 0, "texas", "lappland", "拉普兰德", RelationshipType.RIVAL, 70, isPreset = true, note = "德克萨斯的警惕"),
            Relationship(id = 0, "penance", "texas", "德克萨斯", RelationshipType.FRIEND, 55, isPreset = true, note = "叙拉古同乡共鸣"),
            // === 伊比利亚 ===
            Relationship(id = 0, "thorns", "elysium", "极境", RelationshipType.CLOSE_FRIEND, 75, isPreset = true, note = "伊比利亚好友"),
            Relationship(id = 0, "elysium", "thorns", "棘刺", RelationshipType.CLOSE_FRIEND, 75, isPreset = true, note = "极境与棘刺搭档"),
            Relationship(id = 0, "irene", "specter", "幽灵鲨", RelationshipType.COMRADE, 70, isPreset = true, note = "愚人号战友"),
            Relationship(id = 0, "specter", "irene", "艾丽妮", RelationshipType.COMRADE, 70, isPreset = true, note = "幽灵鲨与艾丽妮"),
            Relationship(id = 0, "weedy", "thorns", "棘刺", RelationshipType.FRIEND, 50, isPreset = true, note = "阿戈尔出身共鸣"),
            // === 使徒 ===
            Relationship(id = 0, "shining", "nightingale", "夜莺", RelationshipType.CLOSE_FRIEND, 90, isPreset = true, note = "闪灵守护夜莺"),
            Relationship(id = 0, "nightingale", "shining", "闪灵", RelationshipType.CLOSE_FRIEND, 90, isPreset = true, note = "夜莺依赖闪灵"),
            Relationship(id = 0, "nearl", "shining", "闪灵", RelationshipType.COMRADE, 75, isPreset = true, note = "使徒的同行者"),
            Relationship(id = 0, "shining", "nearl", "临光", RelationshipType.COMRADE, 75, isPreset = true, note = "闪灵与临光并肩"),
            // === 乌萨斯 ===
            Relationship(id = 0, "rosa", "absinthe", "苦艾", RelationshipType.FRIEND, 60, isPreset = true, note = "乌萨斯出身的前后辈"),
            Relationship(id = 0, "absinthe", "rosa", "早露", RelationshipType.FRIEND, 60, isPreset = true, note = "苦艾与早露同病相怜"),
            Relationship(id = 0, "winter", "absinthe", "苦艾", RelationshipType.FRIEND, 55, isPreset = true, note = "乌萨斯同乡关心"),
            // === 整合运动 ===
            Relationship(id = 0, "frostnova", "crownslayer", "弑君者", RelationshipType.COMRADE, 60, isPreset = true, note = "整合运动干部"),
            Relationship(id = 0, "crownslayer", "frostnova", "霜星", RelationshipType.COMRADE, 55, isPreset = true, note = "弑君者与霜星"),
            // === 终末地 ===
            Relationship(id = 0, "administrator", "perlica", "佩丽卡", RelationshipType.BOSS, 80, isPreset = true, note = "终末地指挥体系"),
            Relationship(id = 0, "perlica", "administrator", "管理员", RelationshipType.SUBORDINATE, 80, isPreset = true, note = "佩丽卡执行指令"),
            Relationship(id = 0, "chen_qianyu", "perlica", "佩丽卡", RelationshipType.SUBORDINATE, 75, isPreset = true, note = "陈千语是护卫"),
            Relationship(id = 0, "perlica", "chen_qianyu", "陈千语", RelationshipType.BOSS, 75, isPreset = true, note = "佩丽卡信任陈千语"),
            Relationship(id = 0, "levantine", "administrator", "管理员", RelationshipType.SUBORDINATE, 70, isPreset = true, note = "莱万汀直属管理员"),
            Relationship(id = 0, "administrator", "levantine", "莱万汀", RelationshipType.BOSS, 70, isPreset = true, note = "管理员指挥莱万汀"),
            Relationship(id = 0, "li_zhiyan", "perlica", "佩丽卡", RelationshipType.TEAMMATE, 65, isPreset = true, note = "终末地近战干员"),
            Relationship(id = 0, "perlica", "li_zhiyan", "李织烟", RelationshipType.TEAMMATE, 65, isPreset = true, note = "佩丽卡认可李织烟"),
            Relationship(id = 0, "zhuang_fangyi", "tangtang", "汤汤", RelationshipType.FRIEND, 65, isPreset = true, note = "武陵城旧识"),
            Relationship(id = 0, "tangtang", "zhuang_fangyi", "庄方宜", RelationshipType.FRIEND, 65, isPreset = true, note = "汤汤与庄方宜"),
            Relationship(id = 0, "mifu", "zhuang_fangyi", "庄方宜", RelationshipType.SUBORDINATE, 75, isPreset = true, note = "弭弗效忠庄方宜"),
            Relationship(id = 0, "zhuang_fangyi", "mifu", "弭弗", RelationshipType.BOSS, 75, isPreset = true, note = "庄方宜信赖弭弗"),
            Relationship(id = 0, "mifu", "tangtang", "汤汤", RelationshipType.RIVAL, 60, isPreset = true, note = "弭弗与汤汤不对付"),
            Relationship(id = 0, "tangtang", "mifu", "弭弗", RelationshipType.RIVAL, 60, isPreset = true, note = "汤汤与弭弗的死对头"),
            Relationship(id = 0, "loxy", "wolfguard", "狼卫", RelationshipType.LITTLE_SISTER, 85, isPreset = true, note = "洛茜是狼卫的妹妹"),
            Relationship(id = 0, "wolfguard", "loxy", "洛茜", RelationshipType.BIG_BROTHER, 85, isPreset = true, note = "狼卫守护妹妹"),
            Relationship(id = 0, "etra", "fluorite", "萤石", RelationshipType.TEAMMATE, 65, isPreset = true, note = "Z7行动组搭档"),
            Relationship(id = 0, "fluorite", "etra", "埃特拉", RelationshipType.TEAMMATE, 65, isPreset = true, note = "Z7行动组搭档"),
            Relationship(id = 0, "adahir", "nephis", "聂菲斯", RelationshipType.COMRADE, 70, isPreset = true, note = "巫族同行伙伴"),
            Relationship(id = 0, "nephis", "adahir", "阿达希尔", RelationshipType.COMRADE, 70, isPreset = true, note = "聂菲斯与阿达希尔"),
            Relationship(id = 0, "saixi", "yvonne", "伊冯", RelationshipType.FRIEND, 55, isPreset = true, note = "技术专家的交流"),
            Relationship(id = 0, "yvonne", "saixi", "赛希", RelationshipType.FRIEND, 55, isPreset = true, note = "伊冯与赛希的技术共鸣"),
            Relationship(id = 0, "chen_qianyu", "administrator", "管理员", RelationshipType.SUBORDINATE, 70, isPreset = true, note = "陈千语听从管理员"),
            // === 其他 ===
            Relationship(id = 0, "blaze", "rosmontis", "迷迭香", RelationshipType.CLOSE_FRIEND, 75, isPreset = true, note = "煌照顾迷迭香"),
            Relationship(id = 0, "rosmontis", "blaze", "煌", RelationshipType.CLOSE_FRIEND, 75, isPreset = true, note = "迷迭香依赖煌"),
            Relationship(id = 0, "tequila", "chen", "陈", RelationshipType.FRIEND, 60, isPreset = true, note = "龙舌兰经陈引荐"),
            Relationship(id = 0, "chen", "tequila", "龙舌兰", RelationshipType.FRIEND, 60, isPreset = true, note = "陈信任龙舌兰")
        )
        relationships.forEach { rel ->
            db.relationshipsQueries.insertRelationship(rel.operatorId, rel.relatedOperatorId, rel.relatedOperatorName, rel.type.name, rel.intimacy.toLong(), if (rel.isPreset) 1L else 0L, rel.note)
        }
    }

    private fun mapRelationship(id: Long, operatorId: String, relatedOperatorId: String, relatedOperatorName: String, type: String, intimacy: Long, isPreset: Long, note: String) =
        Relationship(id, operatorId, relatedOperatorId, relatedOperatorName, try { RelationshipType.valueOf(type) } catch (_: Exception) { RelationshipType.values().first() }, intimacy.toInt(), isPreset != 0L, note)

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
        db.operatorsQueries.getOperator(centerId) { id, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
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
                val name = db.operatorsQueries.getOperator(rel.operatorId) { _, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> name }.executeAsOneOrNull() ?: rel.operatorId
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
            val now = Clock.System.now().toEpochMilliseconds()
            val anchors = db.memoryAnchorsQueries.getPublicAnchors(rel.relatedOperatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt ->
                MemoryAnchor(id, sid, opId, try { AnchorType.valueOf(type) } catch (_: Exception) { AnchorType.EVENT }, content, isPrivate != 0L, createdAt, expiresAt)
            }.executeAsList()
            for (a in anchors) { allAnchors.add(rel.relatedOperatorName to a) }
        }
        allAnchors.sortedByDescending { it.second.createdAt }.take(10).joinToString("\n") { "${it.first}：${it.second.content}" }
    }
}
