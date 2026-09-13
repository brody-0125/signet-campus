package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import work.brodykim.campus.application.CredentialRepository
import work.brodykim.campus.application.IssuedCredential
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class PostgresCredentials(private val jdbc: JdbcTemplate) : CredentialRepository {
    override fun revokedCredentialIds(): List<String> = jdbc.queryForList(
        "SELECT document->>'id' FROM credentials WHERE revoked_at IS NOT NULL ORDER BY id", String::class.java)
    override fun find(id: UUID) = jdbc.query("SELECT * FROM credentials WHERE id = ?", { rs, _ -> read(rs) }, id).singleOrNull()
    override fun findBySubmission(id: UUID) = jdbc.query("SELECT * FROM credentials WHERE submission_id = ?", { rs, _ -> read(rs) }, id).singleOrNull()

    @Transactional
    override fun saveIfAbsent(record: IssuedCredential): IssuedCredential {
        jdbc.update("""INSERT INTO credentials (id, submission_id, learner_id, document, issued_at, valid_until)
            SELECT ?, id, learner_id, ?::jsonb, ?, ? FROM submissions WHERE id = ? AND learner_id = ? AND status = 'APPROVED'
            ON CONFLICT (submission_id) DO NOTHING""", record.id, record.document, Timestamp.from(record.issuedAt),
            Timestamp.from(record.validUntil), record.submissionId, record.learnerId)
        return checkNotNull(findBySubmission(record.submissionId)) { "Approved submission required" }
    }

    override fun revoke(id: UUID, actorId: UUID, at: Instant) = jdbc.update(
        "UPDATE credentials SET revoked_at = COALESCE(revoked_at, ?), revoked_by = COALESCE(revoked_by, ?) WHERE id = ?",
        Timestamp.from(at), actorId, id) == 1

    private fun read(rs: ResultSet) = IssuedCredential(rs.getObject("id", UUID::class.java),
        rs.getObject("submission_id", UUID::class.java), rs.getObject("learner_id", UUID::class.java),
        rs.getString("document"), rs.getTimestamp("issued_at").toInstant(), rs.getTimestamp("valid_until").toInstant(),
        rs.getTimestamp("revoked_at")?.toInstant())
}
