package com.rhodes.privatechat

import com.rhodes.privatechat.data.ExportPayload
import com.rhodes.privatechat.data.ExportHelper
import com.rhodes.privatechat.data.backup.BackupFileReader
import com.rhodes.privatechat.data.backup.BackupFileWriter
import com.rhodes.privatechat.data.backup.BackupMediaItem
import com.rhodes.privatechat.data.backup.BackupMediaSource
import com.rhodes.privatechat.data.backup.BackupPayload
import com.rhodes.privatechat.data.backup.BackupValidationResult
import com.rhodes.privatechat.data.backup.BackupRestoreMaintenance
import com.rhodes.privatechat.data.backup.OperatorPackageReader
import com.rhodes.privatechat.data.backup.OperatorPackagePayload
import com.rhodes.privatechat.data.backup.OperatorPackageWriter
import com.rhodes.privatechat.data.backup.OperatorPromptSlots
import com.rhodes.privatechat.data.backup.ReadableChatExporter
import com.rhodes.privatechat.data.backup.BackupRestorePayloadRewriter
import com.rhodes.privatechat.data.OperatorExport
import com.rhodes.privatechat.data.RelationshipExport
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.data.BackupChatDisplayEvent
import com.rhodes.privatechat.shared.model.DispatchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File

class BackupFileContainerTest {
    @Test
    fun writesAndReadsOperatorCardWithoutPrivateWorldline() {
        val output = ByteArrayOutputStream()
        OperatorPackageWriter("1.13.1") { 100L }.write(
            output,
            OperatorPackagePayload(OperatorExport("amiya", "阿米娅"), emptyList()),
        )
        val card = OperatorPackageReader().read(ByteArrayInputStream(output.toByteArray()))

        assertEquals("rhodes-operator", card.manifest.format)
        assertEquals("operator_card", card.manifest.scope)
        assertEquals("阿米娅", card.payload.operator.name)
        assertTrue("chat_history" in card.manifest.excludedCategories)
    }

    @Test
    fun readsSingleOperatorLegacyJsonCard() {
        val legacy = ExportPayload(type = "operators", operators = listOf(OperatorExport("amiya", "阿米娅")))
        val card = OperatorPackageReader().readCompatible(ByteArrayInputStream(ExportHelper.toJson(legacy).encodeToByteArray()))

        assertEquals("阿米娅", card.payload.operator.name)
    }

    @Test
    fun preservesAllPromptSlotsInOperatorCard() {
        val output = ByteArrayOutputStream()
        OperatorPackageWriter("1.13.1").write(
            output,
            OperatorPackagePayload(
                OperatorExport("amiya", "阿米娅"),
                emptyList(),
                promptSlots = OperatorPromptSlots(listOf("私聊一", "私聊二", "私聊三"), listOf("群聊一", "群聊二", "群聊三"), 2, 3),
            ),
        )

        val restored = OperatorPackageReader().read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(listOf("私聊一", "私聊二", "私聊三"), restored.payload.promptSlots?.privateSlots)
        assertEquals(2, restored.payload.promptSlots?.activePrivateSlot)
        assertEquals(3, restored.payload.promptSlots?.activeGroupSlot)
    }

    @Test
    fun exportsReadableChatAsMarkdownAndText() {
        val messages = listOf(
            ChatMessage(1, "s", senderName = "我", content = "你好", timestamp = 1_700_000_000_000L, isMe = true),
            ChatMessage(2, "s", senderName = "阿米娅", content = "你好，博士。", timestamp = 1_700_000_060_000L, isMe = false),
        )
        assertTrue(ReadableChatExporter.markdown("与阿米娅的聊天记录", "博士", messages).contains("博士：你好"))
        assertTrue(ReadableChatExporter.text("与阿米娅的聊天记录", "博士", messages).contains("阿米娅：你好，博士。"))
    }

    @Test
    fun rewritesOnlyPackagedMediaUris() {
        val original = "file:///old/images/avatar.jpg"
        val item = BackupMediaItem("avatar", "media/images/avatar.jpg", originalUri = original)
        val payload = BackupPayload(
            content = ExportPayload(type = "full_backup", operators = listOf(OperatorExport("amiya", "阿米娅", avatarUri = original))),
            media = listOf(item),
        )
        val rewritten = BackupRestorePayloadRewriter.rewriteMediaUris(payload, mapOf("avatar" to File("D:/new/avatar.jpg")))

        assertTrue(rewritten.content.operators.orEmpty().single().avatarUri.contains("avatar.jpg"))
        assertTrue(rewritten.content.operators.orEmpty().single().avatarUri != original)
    }

    @Test
    fun rewritesTypedPortableUserAvatarUri() {
        val original = "file:///old/images/user.jpg"
        val item = BackupMediaItem("avatar", "media/images/user.jpg", originalUri = original)
        val payload = BackupPayload(
            content = ExportPayload(type = "full_backup", settings = mapOf("user_avatar_uri" to "s:$original")),
            media = listOf(item),
        )

        val rewritten = BackupRestorePayloadRewriter.rewriteMediaUris(payload, mapOf("avatar" to File("D:/new/user.jpg")))
        assertEquals("s:${File("D:/new/user.jpg").toURI()}", rewritten.content.settings?.get("user_avatar_uri"))
    }

    @Test
    fun restoreMaintenanceFenceAdvancesGeneration() {
        val before = BackupRestoreMaintenance.currentGeneration()
        val started = BackupRestoreMaintenance.begin()
        assertTrue(BackupRestoreMaintenance.active)
        assertTrue(started > before)

        val finished = BackupRestoreMaintenance.finish()
        assertTrue(!BackupRestoreMaintenance.active)
        assertTrue(finished > started)
    }

    @Test
    fun writesAndReadsVersionedBackupWithMedia() {
        val media = BackupMediaItem("avatar/operator/amiya", "media/avatars/amiya.jpg", "image/jpeg")
        val payload = BackupPayload(
            content = ExportPayload(type = "full_backup"),
            displayEvents = listOf(BackupChatDisplayEvent(8L, 1, "session_amiya", 2L)),
            dispatchRecords = listOf(DispatchRecord("dispatch_1", "巡逻", 1, 100)),
            media = listOf(media),
        )
        val output = ByteArrayOutputStream()

        val manifest = BackupFileWriter("1.13.1", 24) { 1234L }.writeFullBackup(
            output,
            payload,
            listOf(BackupMediaSource(media) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }),
        )
        val archive = BackupFileReader().read(ByteArrayInputStream(output.toByteArray()))

        assertEquals("rhodes-backup", archive.manifest.format)
        assertEquals(1234L, archive.manifest.createdAt)
        assertEquals(manifest.backupId, archive.manifest.backupId)
        assertEquals("full_backup", archive.payload.content.type)
        assertEquals(listOf(media), archive.payload.media)
        assertEquals("session_amiya", archive.payload.displayEvents.single().sessionId)
        assertEquals("dispatch_1", archive.payload.dispatchRecords.single().id)
    }

    @Test
    fun rejectsZipSlipAndMissingManifest() {
        val slip = zipOf("../outside.txt" to "bad".encodeToByteArray())
        val result = BackupFileReader().validate(ByteArrayInputStream(slip))
        assertTrue(result is BackupValidationResult.Invalid)

        val missingManifest = zipOf("data/backup.json" to "{}".encodeToByteArray())
        assertTrue(BackupFileReader().validate(ByteArrayInputStream(missingManifest)) is BackupValidationResult.Invalid)
    }

    @Test
    fun rejectsTamperedPayload() {
        val payload = BackupPayload(ExportPayload(type = "full_backup"))
        val output = ByteArrayOutputStream()
        BackupFileWriter("1.13.1", 24).writeFullBackup(output, payload)
        val tampered = output.toByteArray().clone()
        tampered[tampered.indexOfFirst { it == 'f'.code.toByte() }] = 'x'.code.toByte()

        assertTrue(BackupFileReader().validate(ByteArrayInputStream(tampered)) is BackupValidationResult.Invalid)
    }

    @Test
    fun rejectsDuplicateMediaManifestEntries() {
        val duplicate = BackupMediaItem("one", "media/images/same.jpg")
        val payload = BackupPayload(ExportPayload(type = "full_backup"), media = listOf(duplicate, duplicate.copy(mediaId = "two")))
        val output = ByteArrayOutputStream()

        val error = runCatching {
            BackupFileWriter("1.13.1", 24).writeFullBackup(
                output,
                payload,
                listOf(BackupMediaSource(duplicate) { ByteArrayInputStream(byteArrayOf(1)) }),
            )
        }.exceptionOrNull()

        assertTrue(error != null)
    }

    @Test
    fun rejectsFullBackupWithDanglingRelationship() {
        val payload = BackupPayload(
            ExportPayload(
                type = "full_backup",
                operators = listOf(OperatorExport("amiya", "阿米娅")),
                relationships = listOf(RelationshipExport("amiya", "missing", "不存在", "FRIEND")),
            )
        )
        val output = ByteArrayOutputStream()
        BackupFileWriter("1.13.1", 24).writeFullBackup(output, payload)

        assertTrue(BackupFileReader().validate(ByteArrayInputStream(output.toByteArray())) is BackupValidationResult.Invalid)
    }

    @Test
    fun readableMarkdownEscapesMessageFormatting() {
        val content = ReadableChatExporter.markdown(
            "# 标题",
            "博士",
            listOf(ChatMessage(1, "s", senderName = "阿米娅", content = "# 不是标题 [链接](https://example.com)", timestamp = 1L, isMe = false)),
        )

        assertTrue(content.contains("\\# 不是标题 \\[链接\\]"))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }
}
