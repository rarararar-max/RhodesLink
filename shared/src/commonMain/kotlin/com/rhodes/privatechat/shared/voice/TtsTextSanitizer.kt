package com.rhodes.privatechat.shared.voice

/** Removes stage directions so speech engines receive only spoken dialogue. */
fun prepareTtsSpeech(raw: String, maxChars: Int, fallback: String): String {
    var text = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    text = text.replace(Regex("""(?is)<\s*(action|动作|narration|旁白)[^>]*>.*?<\s*/\s*\1\s*>"""), "")
    val pairs = listOf('（' to '）', '(' to ')', '[' to ']', '［' to '］', '【' to '】')
    pairs.forEach { (open, close) ->
        while (true) {
            val start = text.indexOf(open)
            if (start < 0) break
            val end = text.indexOf(close, start + 1)
            text = if (end >= 0) text.removeRange(start, end + 1) else text.substring(0, start)
        }
    }
    text = text.replace(Regex("<[^>]{0,120}>"), "")
        .replace(Regex("(?i)(动作|旁白|姿势|表情|语气|音效)[:：][^。！？!？\\n]{0,120}"), "")
        .replace(Regex("(轻声|低声|小声|柔声|温柔地|压低声音|悄悄)说[:：，, ]*"), "")
        .replace(Regex("(沉默(了)?(片刻|一会儿)?|停顿(了)?(片刻|一会儿)?|叹了口气|轻轻叹气|她靠近(了)?(一点)?|他靠近(了)?(一点)?|靠近(了)?(一点)?)[:：，,。 ]*"), "")
        .replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[，,]{2,}"), "，")
        .replace(Regex("[。]{2,}"), "。")
        .replace(Regex("[！!]{2,}"), "！")
        .replace(Regex("[？?]{2,}"), "？")
        .trim(' ', '，', ',', '。', '：', ':', '；', ';')
    if (text.isBlank()) text = fallback
    if (text.length <= maxChars) return text
    val cut = text.take(maxChars)
    val sentenceEnd = cut.indexOfLast { it in "。！？!?" }
    return (if (sentenceEnd >= maxChars / 3) cut.take(sentenceEnd + 1) else cut).trim()
}
