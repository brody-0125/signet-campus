package work.brodykim.campus.application

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class IssuedCredential(val id: UUID, val submissionId: UUID, val learnerId: UUID,
                            val document: String, val issuedAt: Instant, val validUntil: Instant,
                            val revokedAt: Instant? = null)
data class CredentialVerification(val status: String, val valid: Boolean = status == "VALID")

interface CredentialRepository {
    fun find(id: UUID): IssuedCredential?
    fun findBySubmission(id: UUID): IssuedCredential?
    fun saveIfAbsent(record: IssuedCredential): IssuedCredential
    fun revoke(id: UUID, actorId: UUID, at: Instant): Boolean
}
interface CredentialCryptography {
    fun publicProfile(): Map<String, Any>
    fun issue(id: UUID, email: String, achievement: AchievementSummary, at: Instant, until: Instant): String
    fun verify(document: String): Boolean
    fun sameDocument(left: String, right: String): Boolean
}

class CredentialService(private val submissions: SubmissionRepository, private val repository: CredentialRepository,
                        private val crypto: CredentialCryptography, private val clock: Clock) {
    fun issue(actor: Actor, submissionId: UUID, verifiedEmail: String): IssuedCredential {
        val submission = submissions.find(submissionId)?.submission ?: throw SubmissionNotFound()
        if (actor.id != submission.learnerId) throw SubmissionNotFound()
        submission.requireApproved()
        repository.findBySubmission(submissionId)?.let { return it }
        require(verifiedEmail.isNotBlank() && verifiedEmail.length <= 254 && '@' in verifiedEmail)
        val achievement = submissions.achievements().first { it.id == submission.achievementId }
        val id = UUID.randomUUID()
        val at = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        val until = at.plus(365, ChronoUnit.DAYS)
        return repository.saveIfAbsent(IssuedCredential(id, submissionId, actor.id,
            crypto.issue(id, verifiedEmail, achievement, at, until), at, until))
    }
    fun get(actor: Actor, id: UUID): IssuedCredential = repository.find(id)?.takeIf { it.learnerId == actor.id }
        ?: throw SubmissionNotFound()

    fun forSubmission(actor: Actor, id: UUID): IssuedCredential = repository.findBySubmission(id)
        ?.takeIf { it.learnerId == actor.id } ?: throw SubmissionNotFound()

    fun revoke(actor: Actor, id: UUID) {
        if (!actor.reviewer) throw ReviewForbidden()
        if (!repository.revoke(id, actor.id, clock.instant())) throw SubmissionNotFound()
    }

    /** Verifies only credentials in this issuer's registry; never resolves URLs supplied by a caller. */
    fun verify(id: UUID, document: String): CredentialVerification {
        val record = repository.find(id) ?: return CredentialVerification("UNKNOWN_CREDENTIAL")
        val result = when {
            !crypto.sameDocument(record.document, document) -> "ALTERED"
            !crypto.verify(record.document) -> "INVALID_PROOF"
            record.revokedAt != null -> "REVOKED"
            clock.instant().isBefore(record.issuedAt) -> "NOT_YET_VALID"
            !clock.instant().isBefore(record.validUntil) -> "EXPIRED"
            else -> "VALID"
        }
        return CredentialVerification(result)
    }
}
