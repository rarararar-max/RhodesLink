package com.rhodes.privatechat.shared.knowledge

data class KnowledgeBaseChunkDraft(
    val ordinal: Int,
    val sourceHeading: String,
    val content: String,
)

data class KnowledgeBaseImportPreview(
    val normalizedContent: String,
    val chunks: List<KnowledgeBaseChunkDraft>,
)

class KnowledgeBaseImportException(message: String) : IllegalArgumentException(message)

object KnowledgeBaseTextProcessor {
    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    const val MAX_CHARACTERS = 200_000
    const val MAX_CHUNKS = 500
    const val TARGET_CHUNK_LENGTH = 450
    const val MIN_CHUNK_LENGTH = 120
    const val MAX_CHUNK_LENGTH = 1_000
    const val OVERLAP_LENGTH = 80

    fun prepare(fileName: String, bytes: ByteArray): KnowledgeBaseImportPreview {
        require(bytes.size <= MAX_FILE_BYTES) { "知识库文件不能超过 2 MB" }
        val format = fileName.substringAfterLast('.', "").lowercase()
        require(format == "txt" || format == "md") { "仅支持 TXT 或 MD 文件" }
        val text = bytes.decodeToString()
        require('\uFFFD' !in text) { "文件不是有效的 UTF-8 文本，请转换编码后重试" }
        return prepareText(text, format)
    }

    fun prepareText(rawContent: String, sourceFormat: String = "txt"): KnowledgeBaseImportPreview {
        require(sourceFormat.lowercase() in setOf("txt", "md")) { "仅支持 TXT 或 MD 内容" }
        val normalized = normalize(rawContent)
        require(normalized.isNotBlank()) { "知识库内容不能为空" }
        require(normalized.length <= MAX_CHARACTERS) { "知识库正文不能超过 20 万字符" }
        val chunks = split(normalized, sourceFormat.lowercase())
        require(chunks.isNotEmpty()) { "未能识别有效的知识库内容" }
        require(chunks.size <= MAX_CHUNKS) { "分段超过 $MAX_CHUNKS 段，请拆分为多本知识库" }
        return KnowledgeBaseImportPreview(normalized, chunks)
    }

    private fun normalize(raw: String): String = raw
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .lineSequence()
        .map { it.trimEnd() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun split(content: String, sourceFormat: String): List<KnowledgeBaseChunkDraft> {
        val sections = if (sourceFormat == "md") markdownSections(content) else listOf("" to content)
        val chunks = mutableListOf<Pair<String, String>>()
        sections.forEach { (heading, sectionContent) ->
            splitSection(sectionContent).forEach { piece -> chunks += heading to piece }
        }
        return chunks.mapIndexed { index, (heading, chunk) -> KnowledgeBaseChunkDraft(index + 1, heading, chunk) }
    }

    private fun markdownSections(content: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val headings = mutableListOf<String>()
        val body = StringBuilder()
        fun flush() {
            val text = body.toString().trim()
            if (text.isNotBlank()) result += headings.joinToString(" / ") to text
            body.clear()
        }
        content.lineSequence().forEach { line ->
            val match = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$").matchEntire(line)
            if (match == null) {
                body.appendLine(line)
            } else {
                flush()
                val level = match.groupValues[1].length
                while (headings.size >= level) headings.removeLast()
                headings += match.groupValues[2].trim()
            }
        }
        flush()
        return result.ifEmpty { listOf("" to content) }
    }

    private fun splitSection(section: String): List<String> {
        val paragraphs = section.split(Regex("\n{2,}"))
            .flatMap { paragraph -> splitLongParagraph(paragraph.trim()) }
            .filter(String::isNotBlank)
        val merged = mutableListOf<String>()
        paragraphs.forEach { paragraph ->
            val last = merged.lastOrNull()
            if (last != null && last.length < MIN_CHUNK_LENGTH && last.length + paragraph.length + 2 <= MAX_CHUNK_LENGTH) {
                merged[merged.lastIndex] = "$last\n\n$paragraph"
            } else {
                merged += paragraph
            }
        }
        return packChunks(merged)
    }

    private fun splitLongParagraph(paragraph: String): List<String> {
        if (paragraph.length <= TARGET_CHUNK_LENGTH) return listOf(paragraph)
        val sentences = paragraph.split(Regex("(?<=[。！？；.!?;])\\s*|\\n"))
            .map(String::trim)
            .filter(String::isNotBlank)
        if (sentences.size <= 1) return paragraph.chunked(TARGET_CHUNK_LENGTH)
        return packChunks(sentences, separator = "")
    }

    private fun packChunks(parts: List<String>, separator: String = "\n\n"): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        parts.forEach { part ->
            if (part.length > MAX_CHUNK_LENGTH) {
                if (current.isNotEmpty()) {
                    result += current.toString().trim()
                    current = StringBuilder()
                }
                result += part.chunked(MAX_CHUNK_LENGTH)
                return@forEach
            }
            val projectedLength = current.length + if (current.isEmpty()) 0 else separator.length + part.length
            if (current.isNotEmpty() && projectedLength > TARGET_CHUNK_LENGTH) {
                result += current.toString().trim()
                val overlap = current.toString().takeLast(OVERLAP_LENGTH).trim()
                current = StringBuilder(overlap)
            }
            if (current.isNotEmpty()) current.append(separator)
            current.append(part)
        }
        if (current.isNotEmpty()) result += current.toString().trim()
        return result.filter(String::isNotBlank)
    }
}
