package work.brodykim.campus.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class SubmissionRestoreTest : StringSpec({
    val id = UUID.randomUUID()
    val learner = UUID.randomUUID()
    val achievement = UUID.randomUUID()
    val reviewer = UUID.randomUUID()
    val at = Instant.parse("2026-09-14T01:00:00Z")
    fun restore(status: ReviewStatus, review: Review?, revision: Int = 1) =
        EvidenceSubmission.restore(id, learner, achievement, "Evidence", at, status, review, revision)

    "persisted states preserve revisions and enforce the review invariants" {
        restore(ReviewStatus.PENDING, null).revision shouldBe 1
        restore(ReviewStatus.APPROVED, Review(reviewer, at, null)).requireApproved()
        restore(ReviewStatus.REJECTED, Review(reviewer, at, "Reason")).status shouldBe ReviewStatus.REJECTED
        shouldThrow<IllegalArgumentException> { restore(ReviewStatus.PENDING, null, -1) }
        shouldThrow<IllegalArgumentException> { restore(ReviewStatus.PENDING, Review(reviewer, at, null)) }
        shouldThrow<IllegalArgumentException> { restore(ReviewStatus.APPROVED, null) }
        shouldThrow<IllegalArgumentException> { restore(ReviewStatus.APPROVED, Review(reviewer, at, "Reason")) }
        shouldThrow<IllegalArgumentException> { restore(ReviewStatus.REJECTED, null) }
        shouldThrow<IllegalArgumentException> { restore(ReviewStatus.REJECTED, Review(reviewer, at, null)) }
        shouldThrow<IllegalArgumentException> { restore(ReviewStatus.APPROVED, Review(learner, at, null)) }
    }
})
