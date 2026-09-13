package work.brodykim.campus.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import work.brodykim.campus.domain.EvidenceSubmission
import work.brodykim.campus.domain.ReviewStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SubmissionServiceTest : StringSpec({
    val learner = Actor(UUID.randomUUID(), false)
    val reviewer = Actor(UUID.randomUUID(), true)
    val stranger = Actor(UUID.randomUUID(), false)
    val now = Instant.parse("2026-09-14T01:00:00Z")

    fun service(): SubmissionService = SubmissionService(MemorySubmissions(), Clock.fixed(now, ZoneOffset.UTC))

    "submission ownership comes from authenticated actor" {
        val service = service()
        val submitted = service.submit(learner, UUID.randomUUID(), "Evidence")
        submitted.submission.learnerId shouldBe learner.id
        service.get(learner, submitted.submission.id).version shouldBe 0
        service.get(reviewer, submitted.submission.id) shouldBe submitted
        shouldThrow<SubmissionNotFound> { service.get(stranger, submitted.submission.id) }
    }

    "only a reviewer can approve and only pending evidence can be approved" {
        val service = service()
        val id = service.submit(learner, UUID.randomUUID(), "Evidence").submission.id
        shouldThrow<ReviewForbidden> { service.approve(learner, id, 0) }
        service.approve(reviewer, id, 0).submission.status shouldBe ReviewStatus.APPROVED
        shouldThrow<SubmissionConflict> { service.approve(reviewer, id, 0) }
    }

    "rejection and owner resubmission preserve optimistic version" {
        val service = service()
        val id = service.submit(learner, UUID.randomUUID(), "Evidence").submission.id
        val rejected = service.reject(reviewer, id, 0, "Need keyboard evidence")
        rejected.version shouldBe 1
        shouldThrow<SubmissionNotFound> { service.resubmit(stranger, id, 1, "Replacement") }
        shouldThrow<SubmissionConflict> { service.resubmit(learner, id, 0, "Replacement") }
        val corrected = service.resubmit(learner, id, 1, "Keyboard audit")
        corrected.version shouldBe 2
        corrected.submission.revision shouldBe 1
        corrected.submission.status shouldBe ReviewStatus.PENDING
    }

    "missing IDs do not reveal records and self review is rejected" {
        val service = service()
        shouldThrow<SubmissionNotFound> { service.get(learner, UUID.randomUUID()) }
        val id = service.submit(reviewer, UUID.randomUUID(), "Evidence").submission.id
        shouldThrow<IllegalArgumentException> { service.approve(reviewer, id, 0) }
    }
})

private class MemorySubmissions : SubmissionRepository {
    override fun achievements(): List<AchievementSummary> = emptyList()
    override fun list(learnerId: UUID?, offset: Int, limit: Int): List<StoredSubmission> = records.values
        .filter { if (learnerId == null) it.submission.status == ReviewStatus.PENDING else it.submission.learnerId == learnerId }
        .drop(offset).take(limit)
    private val records = mutableMapOf<UUID, StoredSubmission>()
    override fun create(submission: EvidenceSubmission): StoredSubmission = StoredSubmission(submission, 0).also {
        records[submission.id] = it
    }
    override fun find(id: UUID): StoredSubmission? = records[id]
    override fun update(submission: EvidenceSubmission, expectedVersion: Long): StoredSubmission {
        if (records[submission.id]?.version != expectedVersion) throw SubmissionConflict()
        return StoredSubmission(submission, expectedVersion + 1).also { records[submission.id] = it }
    }
}
