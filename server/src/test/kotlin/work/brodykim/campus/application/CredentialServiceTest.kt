package work.brodykim.campus.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import work.brodykim.campus.domain.EvidenceSubmission
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CredentialServiceTest : StringSpec({
    val now = Instant.parse("2026-09-14T00:00:00Z")
    val owner = Actor(UUID.randomUUID(), false)
    val reviewer = Actor(UUID.randomUUID(), true)
    val achievement = AchievementSummary(UUID.randomUUID(), "Accessibility", "Keyboard audit")
    val pending = EvidenceSubmission.submit(UUID.randomUUID(), owner.id, achievement.id, "Private evidence", now)
    val approved = pending.approve(reviewer.id, now)
    fun service(submission: EvidenceSubmission, store: MemoryCredentials = MemoryCredentials(), clock: Instant = now): CredentialService {
        val submissions = object : SubmissionRepository {
            override fun achievements() = listOf(achievement)
            override fun list(learnerId: UUID?, offset: Int, limit: Int) = emptyList<StoredSubmission>()
            override fun find(id: UUID) = if (id == submission.id) StoredSubmission(submission, 1) else null
            override fun create(submission: EvidenceSubmission): StoredSubmission = error("Unused")
            override fun update(submission: EvidenceSubmission, expectedVersion: Long): StoredSubmission = error("Unused")
        }
        val crypto = object : CredentialCryptography {
            override fun publicProfile() = emptyMap<String, Any>()
            override fun issue(id: UUID, email: String, achievement: AchievementSummary, at: Instant, until: Instant, pathway: Boolean) = "signed:$id"
            override fun verify(document: String) = document.startsWith("signed:")
            override fun sameDocument(left: String, right: String) = left == right
        }
        return CredentialService(submissions, store, crypto, Clock.fixed(clock, ZoneOffset.UTC))
    }
    "only approved evidence owned by the recipient can be issued" {
        shouldThrow<IllegalStateException> { service(pending).issue(owner, pending.id, "learner@example.test") }
        shouldThrow<SubmissionNotFound> { service(approved).issue(reviewer, approved.id, "learner@example.test") }
        shouldThrow<IllegalArgumentException> { service(approved).issue(owner, approved.id, "") }
    }
    "issuance is idempotent and private evidence is not copied into the credential" {
        val store = MemoryCredentials()
        val service = service(approved, store)
        val first = service.issue(owner, approved.id, "learner@example.test")
        service.issue(owner, approved.id, "learner@example.test") shouldBe first
        store.records.size shouldBe 1
        first.document.contains("Private evidence") shouldBe false
        shouldThrow<SubmissionNotFound> { service.get(reviewer, first.id) }
    }
    "verification rejects altered documents unknown records expiry and revocation" {
        val store = MemoryCredentials()
        val service = service(approved, store)
        val record = service.issue(owner, approved.id, "learner@example.test")
        service.verify(record.id, record.document).status shouldBe "VALID"
        service.verify(record.id, "altered").status shouldBe "ALTERED"
        service.verify(UUID.randomUUID(), record.document).status shouldBe "UNKNOWN_CREDENTIAL"
        service(approved, store, record.validUntil).verify(record.id, record.document).status shouldBe "EXPIRED"
        service(approved, store, now.minusSeconds(1)).verify(record.id, record.document).status shouldBe "NOT_YET_VALID"
        shouldThrow<ReviewForbidden> { service.revoke(owner, record.id) }
        service.revoke(reviewer, record.id)
        service.verify(record.id, record.document).status shouldBe "REVOKED"
    }
})

private class MemoryCredentials : CredentialRepository {
    override fun findByPathway(id: UUID, learnerId: UUID): IssuedCredential? = error("Unused")
    override fun savePathwayIfAbsent(record: IssuedCredential): IssuedCredential = error("Unused")
    val records = mutableMapOf<UUID, IssuedCredential>()
    override fun revokedCredentialIds() = records.values.filter { it.revokedAt != null }.map { it.id.toString() }
    override fun find(id: UUID) = records[id]
    override fun findBySubmission(id: UUID) = records.values.find { it.submissionId == id }
    override fun saveIfAbsent(record: IssuedCredential): IssuedCredential {
        findBySubmission(requireNotNull(record.submissionId))?.let { return it }
        records[record.id] = record
        return record
    }
    override fun revoke(id: UUID, actorId: UUID, at: Instant): Boolean {
        val record = records[id] ?: return false
        records[id] = record.copy(revokedAt = record.revokedAt ?: at)
        return true
    }
}
