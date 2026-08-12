package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.data.OperatorExport
import com.rhodes.privatechat.data.RelationshipExport
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.util.UUID

data class OperatorImportResult(
    val operatorId: String,
    val createdNew: Boolean,
    val importedRelationships: Int,
    val skippedRelationships: Int,
)

enum class OperatorImportMode { NEW, PERSONA_AND_APPEARANCE, FULL_REPLACE }

data class OperatorRelationshipPreview(
    val relationship: RelationshipExport,
    val directTargetId: String? = null,
)

/** Implements the safe first-release role-card contract: no chats, memories, or social history. */
class OperatorPackageService(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
) {
    suspend fun exportCard(context: Context, operatorId: String): Pair<OperatorPackagePayload, BackupMediaSource?> {
        val operator = repository.getOperator(operatorId) ?: throw BackupFormatException("找不到要导出的角色")
        val file = ownedAvatarFile(context, operator.avatarUri)
        val avatar = file?.let { BackupMediaItem("operator-avatar", "media/avatar.${it.extension.ifBlank { "jpg" }}", originalUri = operator.avatarUri) }
        val activePrivateSlot = settings.getInt("operator_prompt_slot_${operatorId}_private", 1).coerceIn(1, 3)
        val activeGroupSlot = settings.getInt("operator_prompt_slot_${operatorId}_group", 1).coerceIn(1, 3)
        val privateSlotValues = promptSlots(operatorId, "private", operator.privatePrompt)
        val groupSlotValues = promptSlots(operatorId, "group", operator.groupPrompt)
        return OperatorPackagePayload(
            // Runtime prompt builders read Operator.privatePrompt/groupPrompt, while the editor
            // reads the preference-backed slots. Keep both representations aligned.
            operator = OperatorExport.fromEntity(operator).copy(
                privatePrompt = privateSlotValues[activePrivateSlot - 1],
                groupPrompt = groupSlotValues[activeGroupSlot - 1],
            ),
            relationships = repository.getRelationships(operatorId).map(RelationshipExport::fromEntity),
            avatar = avatar,
            promptSlots = OperatorPromptSlots(
                privateSlots = privateSlotValues,
                groupSlots = groupSlotValues,
                activePrivateSlot = activePrivateSlot,
                activeGroupSlot = activeGroupSlot,
            ),
        ) to avatar?.let { item -> BackupMediaSource(item) { FileInputStream(file!!) } }
    }

    suspend fun previewRelationships(card: OperatorPackage): List<OperatorRelationshipPreview> =
        card.payload.relationships.map { relationship ->
            OperatorRelationshipPreview(relationship, repository.getOperator(relationship.relatedOperatorId)?.id)
        }

    suspend fun importCard(
        context: Context,
        card: OperatorPackage,
        mode: OperatorImportMode,
        targetOperatorId: String? = null,
        relationshipMappings: Map<String, String> = emptyMap(),
    ): OperatorImportResult {
        val targetId = when (mode) {
            OperatorImportMode.PERSONA_AND_APPEARANCE, OperatorImportMode.FULL_REPLACE -> targetOperatorId?.takeIf { repository.getOperator(it) != null }
                ?: throw BackupFormatException("请选择要更新的现有角色")
            OperatorImportMode.NEW -> UUID.randomUUID().toString()
        }
        val cardOperator = card.payload.operator.toEntity().copy(
            id = targetId,
            name = if (mode == OperatorImportMode.NEW) uniqueImportedName(card.payload.operator.name) else card.payload.operator.name,
            avatarUri = restoreAvatar(context, card, targetId) ?: portableAvatarUri(card.payload.operator.avatarUri),
        )
        val imported = if (mode == OperatorImportMode.PERSONA_AND_APPEARANCE) {
            val current = repository.getOperator(targetId) ?: error("找不到要更新的现有角色")
            current.copy(
                name = cardOperator.name,
                title = cardOperator.title,
                description = cardOperator.description,
                gender = cardOperator.gender,
                avatarUri = cardOperator.avatarUri.ifBlank { current.avatarUri },
                privatePrompt = cardOperator.privatePrompt,
                groupPrompt = cardOperator.groupPrompt,
                memoryInjection = cardOperator.memoryInjection,
                userRelation = cardOperator.userRelation,
                voiceName = cardOperator.voiceName,
                voiceSpeed = cardOperator.voiceSpeed,
                voicePitch = cardOperator.voicePitch,
            )
        } else if (mode == OperatorImportMode.FULL_REPLACE) {
            val current = repository.getOperator(targetId) ?: error("找不到要更新的现有角色")
            cardOperator.copy(avatarUri = cardOperator.avatarUri.ifBlank { current.avatarUri })
        } else cardOperator
        val activePrivatePrompt = card.payload.promptSlots?.let { slots -> slots.privateSlots.getOrNull(slots.activePrivateSlot.coerceIn(1, 3) - 1) }
        val activeGroupPrompt = card.payload.promptSlots?.let { slots -> slots.groupSlots.getOrNull(slots.activeGroupSlot.coerceIn(1, 3) - 1) }
        val runtimeAligned = imported.copy(
            privatePrompt = activePrivatePrompt ?: imported.privatePrompt,
            groupPrompt = activeGroupPrompt ?: imported.groupPrompt,
        )
        repository.insertOperator(runtimeAligned)
        restorePromptSlots(card.payload.promptSlots, runtimeAligned)

        var importedRelations = 0
        var skippedRelations = 0
        if (mode == OperatorImportMode.PERSONA_AND_APPEARANCE) {
            return OperatorImportResult(targetId, false, 0, card.payload.relationships.size)
        }
        if (mode == OperatorImportMode.FULL_REPLACE) repository.deleteRelationshipByOperator(targetId)
        card.payload.relationships.forEach { relationship ->
            val mappedId = relationshipMappings[relationship.relatedOperatorId]
                ?: relationship.relatedOperatorId.takeIf { repository.getOperator(it) != null }
            val related = if (mappedId == null) null else repository.getOperator(mappedId)
            if (related == null || related.id == targetId) {
                skippedRelations++
            } else {
                repository.insertRelationship(
                    relationship.copy(operatorId = targetId, relatedOperatorId = related.id, relatedOperatorName = related.name).toEntity()
                )
                importedRelations++
            }
        }
        return OperatorImportResult(targetId, mode == OperatorImportMode.NEW, importedRelations, skippedRelations)
    }

    private fun ownedAvatarFile(context: Context, value: String): File? {
        val uri = Uri.parse(value)
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null).canonicalFile
        val root = File(context.filesDir, "images").canonicalFile
        return file.takeIf { it.isFile && it.canRead() && it.path.startsWith(root.path + File.separator) }
    }

    private fun restoreAvatar(context: Context, card: OperatorPackage, operatorId: String): String? {
        val bytes = card.avatarBytes ?: return null
        val extension = card.payload.avatar?.archivePath?.substringAfterLast('.', "jpg") ?: "jpg"
        val file = File(File(context.filesDir, "images").apply { mkdirs() }, "operator_${operatorId}_avatar.$extension")
        file.outputStream().use { it.write(bytes) }
        return file.toURI().toString()
    }

    private fun portableAvatarUri(value: String): String =
        when (Uri.parse(value).scheme?.lowercase()) {
            "http", "https" -> value
            else -> ""
        }

    private fun promptSlots(operatorId: String, type: String, fallback: String): List<String> =
        (1..3).map { slot -> settings.getString("operator_prompt_slot_${operatorId}_${type}_$slot", "").ifBlank { if (slot == 1) fallback else "" } }

    private fun restorePromptSlots(saved: OperatorPromptSlots?, operator: com.rhodes.privatechat.shared.model.Operator) {
        // Old cards only contain the active database prompt. Put it into the currently selected
        // slot so the editor does not mask the imported prompt with its preference-backed value.
        if (saved == null) {
            val privateSlot = settings.getInt("operator_prompt_slot_${operator.id}_private", 1).coerceIn(1, 3)
            val groupSlot = settings.getInt("operator_prompt_slot_${operator.id}_group", 1).coerceIn(1, 3)
            settings.putString("operator_prompt_slot_${operator.id}_private_$privateSlot", operator.privatePrompt)
            settings.putString("operator_prompt_slot_${operator.id}_group_$groupSlot", operator.groupPrompt)
            return
        }
        val privateSlots = saved.privateSlots.take(3).padEnd(3, "")
        val groupSlots = saved.groupSlots.take(3).padEnd(3, "")
        (1..3).forEach { slot ->
            settings.putString("operator_prompt_slot_${operator.id}_private_$slot", privateSlots[slot - 1])
            settings.putString("operator_prompt_slot_${operator.id}_group_$slot", groupSlots[slot - 1])
        }
        settings.putInt("operator_prompt_slot_${operator.id}_private", saved.activePrivateSlot.coerceIn(1, 3))
        settings.putInt("operator_prompt_slot_${operator.id}_group", saved.activeGroupSlot.coerceIn(1, 3))
    }

    private fun List<String>.padEnd(size: Int, value: String): List<String> = take(size) + List((size - this.size).coerceAtLeast(0)) { value }

    private suspend fun uniqueImportedName(source: String): String {
        val base = "$source（导入）"
        val operators = repository.getAllOperatorsSync().map { it.name }.toSet()
        if (base !in operators) return base
        var index = 2
        while ("$base $index" in operators) index++
        return "$base $index"
    }
}
