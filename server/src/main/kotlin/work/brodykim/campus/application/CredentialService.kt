package work.brodykim.campus.application

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class IssuedCredential(val id: UUID, val submissionId: UUID?, val learnerId: UUID,
                            val document: String, val issuedAt: Instant, val validUntil: Instant,
                            val revokedAt: Instant? = null, val pathwayId: UUID? = null, val shared: Boolean = false)
data class CredentialVerification(val status: String, val valid: Boolean = status == "VALID")

interface CredentialRepository {
    fun revokedCredentialIds(): List<String>
    fun find(id: UUID): IssuedCredential?
    fun findShared(id: UUID): IssuedCredential?
    fun setSharing(id: UUID, learnerId: UUID, enabled: Boolean): Boolean
    fun findBySubmission(id: UUID): IssuedCredential?
    fun findByPathway(id: UUID, learnerId: UUID): IssuedCredential?
    fun savePathwayIfAbsent(record: IssuedCredential): IssuedCredential
    fun saveIfAbsent(record: IssuedCredential): IssuedCredential
    fun revoke(id: UUID, actorId: UUID, at: Instant): Boolean
}
interface CredentialCryptography {
    fun publicProfile(): Map<String, Any>
    fun issue(id: UUID, email: String, achievement: AchievementSummary, at: Instant, until: Instant, pathway: Boolean = false): String
    fun verify(document: String): Boolean
    fun sameDocument(left: String, right: String): Boolean
}

class PathwayCompletionRequired : RuntimeException("Current credentials required for every completion achievement")

class PathwayCredentialService(private val pathways: PathwayRepository, private val repository: CredentialRepository,
                               private val crypto: CredentialCryptography, private val clock: Clock) {
    fun get(actor: Actor, id: UUID) = repository.findByPathway(id, actor.id) ?: throw PathwayNotFound()

    fun issue(actor: Actor, id: UUID, email: String): IssuedCredential {
        require(email.length <= 254 && email.matches(Regex("[^\\s@,;<>]+@[^\\s@,;<>]+")))
        val pathway = pathways.find(id) ?: throw PathwayNotFound()
        val progress = pathways.progress(id, actor.id) ?: throw PathwayNotFound()
        repository.findByPathway(id, actor.id)?.let { return it }
        if (!progress.completed) throw PathwayCompletionRequired()
        val at = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        val until = at.plus(365, ChronoUnit.DAYS)
        val credentialId = UUID.randomUUID()
        val criteria = "Enrolled in ${pathway.name} and held a current, non-revoked credential for every required achievement at issuance: " +
            pathway.achievementIds.joinToString() + ". This award records completion at issuance; later component expiry or revocation does not cancel it."
        val achievement = AchievementSummary(pathway.id, pathway.name, criteria)
        return repository.savePathwayIfAbsent(IssuedCredential(credentialId, null, actor.id,
            crypto.issue(credentialId, email, achievement, at, until, pathway = true), at, until, pathwayId = id))
    }
}

class CredentialService(private val submissions: SubmissionRepository, private val repository: CredentialRepository,
                        private val crypto: CredentialCryptography, private val clock: Clock) {
    fun shared(id: UUID) = repository.findShared(id)
    fun setSharing(actor: Actor, id: UUID, enabled: Boolean) {
        if (!repository.setSharing(id, actor.id, enabled)) throw SubmissionNotFound()
    }
    fun revokedCredentialIds(): List<String> = repository.revokedCredentialIds()
    fun issue(actor: Actor, submissionId: UUID, verifiedEmail: String): IssuedCredential {
        val submission = submissions.find(submissionId)?.submission ?: throw SubmissionNotFound()
        if (actor.id != submission.learnerId) throw SubmissionNotFound()
        submission.requireApproved()
        repository.findBySubmission(submissionId)?.let { return it }
        require(verifiedEmail.isNotBlank() && verifiedEmail.length <= 254 && '@' in verifiedEmail)
        val achievement = submissions.achievements(includeArchived = true).first { it.id == submission.achievementId }
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
