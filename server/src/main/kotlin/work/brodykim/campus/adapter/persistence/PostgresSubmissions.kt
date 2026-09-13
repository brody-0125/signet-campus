package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import io.github.oshai.kotlinlogging.KotlinLogging
import work.brodykim.campus.application.AchievementSummary
import work.brodykim.campus.application.StoredSubmission
import work.brodykim.campus.application.SubmissionConflict
import work.brodykim.campus.application.SubmissionRepository
import work.brodykim.campus.domain.EvidenceSubmission
import work.brodykim.campus.domain.Review
import work.brodykim.campus.domain.ReviewStatus
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class PostgresSubmissions(private val jdbc: JdbcTemplate) : SubmissionRepository {
    private val logger = KotlinLogging.logger {}

    override fun achievements(): List<AchievementSummary> = jdbc.query("SELECT id, name, criteria FROM achievements ORDER BY name, id") { rs, _ ->
        AchievementSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("criteria"))
    }
    @Transactional
    override fun create(submission: EvidenceSubmission): StoredSubmission {
        jdbc.update("INSERT INTO submissions (id, learner_id, achievement_id, evidence, submitted_at) VALUES (?, ?, ?, ?, ?)",
            submission.id, submission.learnerId, submission.achievementId, submission.evidence, Timestamp.from(submission.submittedAt))
        audit(submission.id)
        return StoredSubmission(submission, 0)
    }

    override fun find(id: UUID): StoredSubmission? =
        jdbc.query("SELECT * FROM submissions WHERE id = ?", { rs, _ -> read(rs) }, id).singleOrNull()

    override fun list(learnerId: UUID?, offset: Int, limit: Int): List<StoredSubmission> =
        if (learnerId == null) jdbc.query("SELECT * FROM submissions WHERE status = 'PENDING' ORDER BY submitted_at, id LIMIT ? OFFSET ?",
            { rs, _ -> read(rs) }, limit, offset)
        else jdbc.query("SELECT * FROM submissions WHERE learner_id = ? ORDER BY submitted_at, id LIMIT ? OFFSET ?",
            { rs, _ -> read(rs) }, learnerId, limit, offset)

    @Transactional
    override fun update(submission: EvidenceSubmission, expectedVersion: Long): StoredSubmission {
        val changed = jdbc.update("""
            UPDATE submissions SET evidence = ?, submitted_at = ?, status = ?, reviewer_id = ?, reviewed_at = ?,
                reason = ?, revision = ?, version = version + 1 WHERE id = ? AND version = ?
        """.trimIndent(), submission.evidence, Timestamp.from(submission.submittedAt), submission.status.name,
            submission.review?.reviewerId, submission.review?.reviewedAt?.let(Timestamp::from),
            submission.review?.reason, submission.revision, submission.id, expectedVersion)
        if (changed != 1) {
            logger.debug { "Submission update rejected due to concurrent modification" }
            throw SubmissionConflict()
        }
        audit(submission.id)
        return StoredSubmission(submission, expectedVersion + 1)
    }

    private fun audit(id: UUID) {
        jdbc.update("INSERT INTO submission_audit (submission_id, version, snapshot) SELECT id, version, to_jsonb(s) FROM submissions s WHERE id = ?", id)
    }

    private fun read(rs: ResultSet): StoredSubmission {
        val reviewerId = rs.getObject("reviewer_id", UUID::class.java)
        val review = reviewerId?.let { Review(it, rs.getTimestamp("reviewed_at").toInstant(), rs.getString("reason")) }
        return StoredSubmission(EvidenceSubmission.restore(rs.getObject("id", UUID::class.java),
            rs.getObject("learner_id", UUID::class.java), rs.getObject("achievement_id", UUID::class.java),
            rs.getString("evidence"), rs.getTimestamp("submitted_at").toInstant(),
            ReviewStatus.valueOf(rs.getString("status")), review, rs.getInt("revision")), rs.getLong("version"))
    }
}
