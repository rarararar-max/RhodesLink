package com.rhodes.privatechat.data.backup

/** Removes whole user-selected categories while keeping referentially required parent records. */
object BackupContentFilter {
    fun apply(payload: BackupPayload, requested: BackupContentSelection): BackupPayload {
        val selection = requested.normalized()
        val content = payload.content
        val filtered = payload.copy(
            content = content.copy(
                operators = content.operators.takeIf { selection.roles },
                relationships = content.relationships.takeIf { selection.roles },
                sessions = content.sessions.takeIf { selection.chats },
                messages = content.messages.takeIf { selection.chats },
                memories = content.memories.takeIf { selection.memories },
                anchors = content.anchors.takeIf { selection.memories },
                memoryItems = content.memoryItems.takeIf { selection.memories },
                moments = content.moments.takeIf { selection.social },
                momentLikes = content.momentLikes.takeIf { selection.social },
                momentComments = content.momentComments.takeIf { selection.social },
                diaries = content.diaries.takeIf { selection.social },
                knowledgeBases = content.knowledgeBases.takeIf { selection.knowledgeBases },
                knowledgeBaseChunks = content.knowledgeBaseChunks.takeIf { selection.knowledgeBases },
                operatorKnowledgeBaseAssignments = content.operatorKnowledgeBaseAssignments.takeIf { selection.knowledgeBases },
                settings = content.settings.takeIf { selection.settings },
            ),
            displayEvents = payload.displayEvents.takeIf { selection.chats }.orEmpty(),
            chatArchives = payload.chatArchives.takeIf { selection.chats }.orEmpty(),
            chatHistorySegments = payload.chatHistorySegments.takeIf { selection.chats }.orEmpty(),
            giftRecords = payload.giftRecords.takeIf { selection.extras }.orEmpty(),
            dispatchRecords = payload.dispatchRecords.takeIf { selection.extras }.orEmpty(),
            mahjongSave = payload.mahjongSave.takeIf { selection.extras },
            sharedExperiences = payload.sharedExperiences.takeIf { selection.social }.orEmpty(),
            sharedExperienceParticipants = payload.sharedExperienceParticipants.takeIf { selection.social }.orEmpty(),
            media = payload.media.takeIf { selection.media }.orEmpty(),
        )
        return if (selection.media) filtered else filtered.withoutLocalMediaUris()
    }

    private fun BackupPayload.withoutLocalMediaUris(): BackupPayload {
        fun portableUri(value: String): String = value.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }.orEmpty()
        fun settingUri(value: String): String = if (value.startsWith("s:")) "s:${portableUri(value.removePrefix("s:"))}" else portableUri(value)
        return copy(
            content = content.copy(
                operators = content.operators?.map { it.copy(avatarUri = portableUri(it.avatarUri)) },
                sessions = content.sessions?.map { it.copy(avatarUri = portableUri(it.avatarUri)) },
                settings = content.settings?.toMutableMap()?.apply {
                    this["user_avatar_uri"] = this["user_avatar_uri"]?.let(::settingUri).orEmpty()
                },
            ),
            giftRecords = giftRecords.map { it.copy(imageUri = portableUri(it.imageUri)) },
        )
    }
}
