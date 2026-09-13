package work.brodykim.campus.domain

import java.time.Instant
import java.util.UUID

enum class ReviewStatus { PENDING, APPROVED, REJECTED }

data class Review(val reviewerId: UUID, val reviewedAt: Instant, val reason: String?)

/** Immutable assessment state; authentication and reviewer permissions belong at the application boundary. */
class EvidenceSubmission private constructor(
    val id: UUID,
    val learnerId: UUID,
    val achievementId: UUID,
    val evidence: String,
    val submittedAt: Instant,
    val status: ReviewStatus,
    val review: Review?,
    val revision: Int,
) {
    fun approve(reviewerId: UUID, at: Instant): EvidenceSubmission =
        decide(reviewerId, at, ReviewStatus.APPROVED, null)

    fun reject(reviewerId: UUID, at: Instant, reason: String): EvidenceSubmission =
        decide(reviewerId, at, ReviewStatus.REJECTED, validatedText(reason, 1000, "Rejection reason"))

    fun resubmit(actorId: UUID, evidence: String, at: Instant): EvidenceSubmission {
        check(status == ReviewStatus.REJECTED) { "Only rejected submissions can be resubmitted" }
        require(actorId == learnerId) { "Only the owner can resubmit evidence" }
        require(!at.isBefore(review!!.reviewedAt)) { "Resubmission cannot precede review" }
        return EvidenceSubmission(id, learnerId, achievementId, validatedText(evidence, 4000, "Evidence"),
            at, ReviewStatus.PENDING, null, Math.incrementExact(revision))
    }

    fun requireApproved() {
        check(status == ReviewStatus.APPROVED) { "Credential issuance requires approved evidence" }
    }

    private fun decide(reviewerId: UUID, at: Instant, decision: ReviewStatus, reason: String?): EvidenceSubmission {
        check(status == ReviewStatus.PENDING) { "Submission has already been reviewed" }
        require(reviewerId != learnerId) { "Self-review is not permitted" }
        require(!at.isBefore(submittedAt)) { "Review cannot precede submission" }
        return EvidenceSubmission(id, learnerId, achievementId, evidence, submittedAt,
            decision, Review(reviewerId, at, reason), revision)
    }

    companion object {
        fun submit(id: UUID, learnerId: UUID, achievementId: UUID, evidence: String, at: Instant) =
            EvidenceSubmission(id, learnerId, achievementId, validatedText(evidence, 4000, "Evidence"),
                at, ReviewStatus.PENDING, null, 0)

        private fun validatedText(value: String, maxLength: Int, label: String): String = value.trim().also {
            require(it.isNotEmpty() && it.length <= maxLength) { "$label must contain 1 to $maxLength characters" }
        }
    }
}
