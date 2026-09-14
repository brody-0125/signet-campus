package work.brodykim.campus.application

import work.brodykim.campus.domain.EvidenceSubmission
import java.time.Clock
import java.util.UUID

data class Actor(val id: UUID, val reviewer: Boolean)
data class StoredSubmission(val submission: EvidenceSubmission, val version: Long)
data class AchievementSummary(val id: UUID, val name: String, val criteria: String, val version: Long = 0, val published: Boolean = false, val archived: Boolean = false)
class SubmissionNotFound : RuntimeException("Submission not found")
class ReviewForbidden : RuntimeException("Reviewer permission required")
class SubmissionConflict : RuntimeException("Submission changed; reload before retrying")

interface SubmissionRepository {
    fun achievements(includeArchived: Boolean = false): List<AchievementSummary>
    fun list(learnerId: UUID?, offset: Int, limit: Int): List<StoredSubmission>
    fun create(submission: EvidenceSubmission): StoredSubmission
    fun find(id: UUID): StoredSubmission?
    fun update(submission: EvidenceSubmission, expectedVersion: Long): StoredSubmission
}

class SubmissionService(private val repository: SubmissionRepository, private val clock: Clock) {
    fun achievements(includeArchived: Boolean = false): List<AchievementSummary> = repository.achievements(includeArchived)
    fun list(actor: Actor, offset: Int, limit: Int): List<StoredSubmission> {
        require(offset >= 0 && limit in 1..100) { "Invalid pagination" }
        return repository.list(if (actor.reviewer) null else actor.id, offset, limit)
    }
    fun submit(actor: Actor, achievementId: UUID, evidence: String): StoredSubmission =
        repository.create(EvidenceSubmission.submit(UUID.randomUUID(), actor.id, achievementId, evidence, clock.instant()))

    fun get(actor: Actor, id: UUID): StoredSubmission {
        val stored = repository.find(id) ?: throw SubmissionNotFound()
        if (!actor.reviewer && actor.id != stored.submission.learnerId) throw SubmissionNotFound()
        return stored
    }

    fun approve(actor: Actor, id: UUID, expectedVersion: Long): StoredSubmission {
        if (!actor.reviewer) throw ReviewForbidden()
        val stored = current(actor, id, expectedVersion)
        return repository.update(stored.submission.approve(actor.id, clock.instant()), expectedVersion)
    }

    fun reject(actor: Actor, id: UUID, expectedVersion: Long, reason: String): StoredSubmission {
        if (!actor.reviewer) throw ReviewForbidden()
        val stored = current(actor, id, expectedVersion)
        return repository.update(stored.submission.reject(actor.id, clock.instant(), reason), expectedVersion)
    }

    fun resubmit(actor: Actor, id: UUID, expectedVersion: Long, evidence: String): StoredSubmission {
        val stored = current(actor, id, expectedVersion)
        if (actor.id != stored.submission.learnerId) throw SubmissionNotFound()
        return repository.update(stored.submission.resubmit(actor.id, evidence, clock.instant()), expectedVersion)
    }

    private fun current(actor: Actor, id: UUID, expectedVersion: Long): StoredSubmission = get(actor, id).also {
        if (it.version != expectedVersion) throw SubmissionConflict()
    }
}
