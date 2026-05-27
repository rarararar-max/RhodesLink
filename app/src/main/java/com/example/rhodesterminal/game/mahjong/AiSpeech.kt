package com.example.rhodesterminal.game.mahjong

import android.util.Log
import kotlin.random.Random

/** 发言气泡 */
data class SpeechBubble(
    val speakerName: String,
    val text: String,
    val expireTime: Long = System.currentTimeMillis() + 3000,
    val priority: Int = 0  // 0=低, 1=中, 2=高
)

/** 发言请求（发送给 AI） */
data class SpeechRequest(
    val speakerName: String,
    val persona: String,
    val event: String,
    val mood: String,
    val action: String,
    val ruleType: String,
    val priority: Int
)

object AiSpeech {
    private const val TAG = "麻将"
    var aiCaller: ((SpeechRequest) -> Unit)? = null

    // 预设句子库（超时兜底）
    private val fallbackLines = mapOf(
        "出牌嘀咕" to listOf("嗯…打这个吧", "这张应该安全", "就这个了", "没什么好犹豫的"),
        "碰牌" to listOf("这个我要了！", "碰！", "等的就是这张"),
        "吃牌" to listOf("借用了~", "吃！", "这个组合不错"),
        "杠" to listOf("杠！", "又凑齐了", "意外收获"),
        "被鸣牌" to listOf("…被看到了", "啧", "居然要走了"),
        "听牌" to listOf("(小声)还差一张…", "快了…", "就在下一张了"),
        "和牌" to listOf("和了！", "赢了！", "就等这一刻！"),
        "放铳" to listOf("怎么会…", "打错了…", "想不到是这个"),
        "流局" to listOf("又流局了…", "大家都太稳了", "这一局白打了"),
        "闲聊" to listOf("说起来今天食堂的菜还不错", "博士昨天是不是又熬夜了？", "最近训练有点累了"),
        "建议打牌" to listOf("要不打这张试试？", "感觉打左边那张好一点", "我觉得留着万字比较好"),
        "建议不确定" to listOf("我也看不出来该打什么…", "博士你自己看着办", "这手牌有点难处理"),
    )

    fun getFallback(action: String): String {
        for ((key, lines) in fallbackLines) {
            if (action.contains(key) || key.contains(action.take(5))) {
                return lines.random()
            }
        }
        return fallbackLines.values.random().random()
    }

    fun requestSpeech(request: SpeechRequest) {
        aiCaller?.invoke(request)
    }
}
