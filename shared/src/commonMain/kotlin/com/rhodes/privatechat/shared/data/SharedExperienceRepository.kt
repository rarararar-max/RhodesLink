package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.model.SharedExperience
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SharedExperienceRepository(private val wrapper: DatabaseWrapper) {
    private val db get() = wrapper.database

    suspend fun saveIfAbsent(experience: SharedExperience, participants: List<String>): Long = withContext(Dispatchers.Default) {
        val existing = db.sharedExperiencesQueries.getSharedExperienceBySource(experience.sourceKind, experience.sourceRefId) { id, sourceKind, sourceRefId, groupId, content, importance, status, createdAt, expiresAt ->
            SharedExperience(id, sourceKind, sourceRefId, groupId, content, importance.toInt(), status, createdAt, expiresAt)
        }.executeAsOneOrNull()
        val id = existing?.id ?: run {
            db.sharedExperiencesQueries.insertSharedExperience(experience.sourceKind, experience.sourceRefId, experience.groupId, experience.content, experience.importance.toLong(), experience.status, experience.createdAt, experience.expiresAt)
            db.sharedExperiencesQueries.getLastSharedExperienceId().executeAsOne()
        }
        participants.distinct().filter { it.isNotBlank() }.forEach { operatorId ->
            db.sharedExperiencesQueries.insertSharedExperienceParticipant(id, operatorId, "participant")
        }
        id
    }

    suspend fun deleteBySource(sourceKind: String, sourceRefId: String) = withContext(Dispatchers.Default) {
        db.transaction {
            db.sharedExperiencesQueries.deleteSharedExperienceParticipantsBySource(sourceKind, sourceRefId)
            db.sharedExperiencesQueries.deleteSharedExperiencesBySource(sourceKind, sourceRefId)
        }
    }
}
