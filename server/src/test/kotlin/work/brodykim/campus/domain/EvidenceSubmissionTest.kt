package work.brodykim.campus.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class EvidenceSubmissionTest : StringSpec({
    val learner = UUID.randomUUID()
    val reviewer = UUID.randomUUID()
    val achievement = UUID.randomUUID()
    val submittedAt = Instant.parse("2026-09-14T01:00:00Z")
    val reviewedAt = submittedAt.plusSeconds(60)
    fun submission(evidence: String = "Accessibility audit and remediation notes") =
        EvidenceSubmission.submit(UUID.randomUUID(), learner, achievement, evidence, submittedAt)

    "new evidence is pending and cannot authorize issuance" {
        val pending = submission()
        pending.status shouldBe ReviewStatus.PENDING
        pending.learnerId shouldBe learner
        pending.achievementId shouldBe achievement
        pending.review shouldBe null
        shouldThrow<IllegalStateException> { pending.requireApproved() }
    }

    "approval preserves evidence and records the reviewer and time" {
        val pending = submission()
        val approved = pending.approve(reviewer, reviewedAt)
        approved.status shouldBe ReviewStatus.APPROVED
        approved.evidence shouldBe pending.evidence
        approved.id shouldBe pending.id
        approved.review shouldBe Review(reviewer, reviewedAt, null)
        approved.requireApproved()
        pending.status shouldBe ReviewStatus.PENDING
    }

    "rejection requires a reason and cannot authorize issuance" {
        val rejected = submission().reject(reviewer, reviewedAt, "Missing keyboard navigation evidence")
        rejected.status shouldBe ReviewStatus.REJECTED
        rejected.review?.reason shouldBe "Missing keyboard navigation evidence"
        shouldThrow<IllegalStateException> { rejected.requireApproved() }
    }

    "learners cannot approve or reject their own evidence" {
        shouldThrow<IllegalArgumentException> { submission().approve(learner, reviewedAt) }
        shouldThrow<IllegalArgumentException> { submission().reject(learner, reviewedAt, "Reason") }
    }

    "review cannot precede submission" {
        shouldThrow<IllegalArgumentException> { submission().approve(reviewer, submittedAt.minusSeconds(1)) }
        shouldThrow<IllegalArgumentException> { submission().reject(reviewer, submittedAt.minusSeconds(1), "Reason") }
        submission().approve(reviewer, submittedAt).status shouldBe ReviewStatus.APPROVED
    }

    "both completed review states reject another decision" {
        listOf(submission().approve(reviewer, reviewedAt), submission().reject(reviewer, reviewedAt, "Reason"))
            .forEach { reviewed ->
                shouldThrow<IllegalStateException> { reviewed.approve(reviewer, reviewedAt) }
                shouldThrow<IllegalStateException> { reviewed.reject(reviewer, reviewedAt, "Other reason") }
            }
    }

    "rejected evidence can be corrected by its owner" {
        val rejected = submission().reject(reviewer, reviewedAt, "Add evidence")
        val corrected = rejected.resubmit(learner, "Updated evidence", reviewedAt.plusSeconds(1))
        corrected.id shouldBe rejected.id
        corrected.learnerId shouldBe learner
        corrected.achievementId shouldBe achievement
        corrected.evidence shouldBe "Updated evidence"
        corrected.submittedAt shouldBe reviewedAt.plusSeconds(1)
        corrected.status shouldBe ReviewStatus.PENDING
        corrected.review shouldBe null
        corrected.revision shouldBe 1
        corrected.approve(reviewer, reviewedAt.plusSeconds(2)).requireApproved()
    }

    "resubmission is forbidden for another learner or a time before review" {
        val rejected = submission().reject(reviewer, reviewedAt, "Reason")
        shouldThrow<IllegalArgumentException> { rejected.resubmit(reviewer, "Updated", reviewedAt) }
        shouldThrow<IllegalArgumentException> { rejected.resubmit(learner, "Updated", submittedAt) }
    }

    "pending and approved submissions cannot be rewritten" {
        listOf(submission(), submission().approve(reviewer, reviewedAt)).forEach {
            shouldThrow<IllegalStateException> { it.resubmit(learner, "Replacement", reviewedAt) }
        }
    }

    "evidence must contain between 1 and 4000 characters after trimming" {
        listOf("", " \n\t", "x".repeat(4001)).forEach {
            shouldThrow<IllegalArgumentException> { submission(it) }
        }
        submission("  Evidence  ").evidence shouldBe "Evidence"
        submission("x".repeat(4000)).evidence.length shouldBe 4000
    }

    "rejection reason must contain between 1 and 1000 characters after trimming" {
        listOf("", " \t", "x".repeat(1001)).forEach {
            shouldThrow<IllegalArgumentException> { submission().reject(reviewer, reviewedAt, it) }
        }
        submission().reject(reviewer, reviewedAt, "  Explain  ").review?.reason shouldBe "Explain"
        submission().reject(reviewer, reviewedAt, "x".repeat(1000)).review?.reason?.length shouldBe 1000
    }

    "corrected evidence is validated with the original submission rules" {
        val rejected = submission().reject(reviewer, reviewedAt, "Reason")
        shouldThrow<IllegalArgumentException> { rejected.resubmit(learner, " ", reviewedAt) }
        shouldThrow<IllegalArgumentException> { rejected.resubmit(learner, "x".repeat(4001), reviewedAt) }
    }
})
