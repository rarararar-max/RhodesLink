package com.rhodes.privatechat.shared.network

/** Finds complete top-level JSON objects or arrays without being confused by strings or escapes. */
object JsonBlockExtractor {
    fun extract(raw: String): List<String> {
        val blocks = mutableListOf<String>()
        var start = -1
        var expectedClose = '\u0000'
        var depth = 0
        var inString = false
        var escaped = false
        raw.forEachIndexed { index, char ->
            if (start < 0) {
                if (char == '{' || char == '[') {
                    start = index
                    expectedClose = if (char == '{') '}' else ']'
                    depth = 1
                }
                return@forEachIndexed
            }
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') inString = !inString
            else if (!inString) {
                if (char == raw[start]) depth++
                else if (char == expectedClose && --depth == 0) {
                    blocks += raw.substring(start, index + 1)
                    start = -1
                }
            }
        }
        return blocks
    }
}
