package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import work.brodykim.campus.application.CredentialRepository
import work.brodykim.campus.application.IssuedCredential
import work.brodykim.campus.application.PathwayCompletionRequired
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
    override fun findByPathway(id: UUID, learnerId: UUID) = jdbc.query(
        "SELECT * FROM credentials WHERE pathway_id = ? AND learner_id = ?", { rs, _ -> read(rs) }, id, learnerId).singleOrNull()

    @Transactional
    override fun savePathwayIfAbsent(record: IssuedCredential): IssuedCredential {
        val pathwayId = requireNotNull(record.pathwayId)
        jdbc.update("""INSERT INTO credentials (id, pathway_id, learner_id, document, issued_at, valid_until)
            SELECT ?, e.pathway_id, e.learner_id, ?::jsonb, ?, ? FROM pathway_enrollments e
            WHERE e.pathway_id = ? AND e.learner_id = ?
                AND EXISTS (SELECT 1 FROM pathway_requirements WHERE pathway_id = e.pathway_id)
                AND NOT EXISTS (SELECT 1 FROM pathway_requirements r WHERE r.pathway_id = e.pathway_id AND NOT EXISTS (
                    SELECT 1 FROM credentials c JOIN submissions s ON s.id = c.submission_id
                    WHERE c.learner_id = e.learner_id AND s.achievement_id = r.achievement_id
                        AND c.revoked_at IS NULL AND c.issued_at <= statement_timestamp() AND c.valid_until > statement_timestamp()
                )) ON CONFLICT (pathway_id, learner_id) DO NOTHING""", record.id, record.document,
            Timestamp.from(record.issuedAt), Timestamp.from(record.validUntil), pathwayId, record.learnerId)
        return findByPathway(pathwayId, record.learnerId) ?: throw PathwayCompletionRequired()
    }

    @Transactional
    override fun saveIfAbsent(record: IssuedCredential): IssuedCredential {
        jdbc.update("""INSERT INTO credentials (id, submission_id, learner_id, document, issued_at, valid_until)
            SELECT ?, id, learner_id, ?::jsonb, ?, ? FROM submissions WHERE id = ? AND learner_id = ? AND status = 'APPROVED'
            ON CONFLICT (submission_id) DO NOTHING""", record.id, record.document, Timestamp.from(record.issuedAt),
            Timestamp.from(record.validUntil), record.submissionId, record.learnerId)
        return checkNotNull(findBySubmission(requireNotNull(record.submissionId))) { "Approved submission required" }
    }

    override fun revoke(id: UUID, actorId: UUID, at: Instant) = jdbc.update(
        "UPDATE credentials SET revoked_at = COALESCE(revoked_at, ?), revoked_by = COALESCE(revoked_by, ?) WHERE id = ?",
        Timestamp.from(at), actorId, id) == 1

    private fun read(rs: ResultSet) = IssuedCredential(rs.getObject("id", UUID::class.java),
        rs.getObject("submission_id", UUID::class.java), rs.getObject("learner_id", UUID::class.java),
        rs.getString("document"), rs.getTimestamp("issued_at").toInstant(), rs.getTimestamp("valid_until").toInstant(),
        rs.getTimestamp("revoked_at")?.toInstant(), rs.getObject("pathway_id", UUID::class.java))
}
