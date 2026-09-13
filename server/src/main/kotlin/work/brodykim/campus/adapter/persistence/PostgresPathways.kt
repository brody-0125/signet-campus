package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import work.brodykim.campus.application.*
import java.util.UUID

@Repository
class PostgresPathways(private val jdbc: JdbcTemplate) : PathwayRepository {
    override fun list(): List<Pathway> = read("")
    override fun find(id: UUID): Pathway? = read("WHERE p.id = ?", id).singleOrNull()
    private fun read(where: String, vararg args: Any): List<Pathway> = jdbc.query(
        """SELECT p.id, p.name, p.description, array_agg(r.achievement_id ORDER BY r.position) AS requirements,
            ARRAY(SELECT achievement_id FROM pathway_prerequisites WHERE pathway_id = p.id ORDER BY position) AS prerequisites
            FROM pathways p JOIN pathway_requirements r ON r.pathway_id = p.id
            $where GROUP BY p.id ORDER BY p.name, p.id""", { rs, _ ->
            Pathway(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("description"),
                (rs.getArray("requirements").array as Array<*>).map { it as UUID },
                (rs.getArray("prerequisites").array as Array<*>).map { it as UUID })
        }, *args)

    @Transactional
    override fun create(pathway: Pathway): Pathway {
        jdbc.update("INSERT INTO pathways (id, name, description) VALUES (?, ?, ?)", pathway.id, pathway.name, pathway.description)
        pathway.achievementIds.forEachIndexed { index, id ->
            jdbc.update("INSERT INTO pathway_requirements (pathway_id, achievement_id, position) VALUES (?, ?, ?)", pathway.id, id, index)
        }
        pathway.prerequisiteAchievementIds.forEachIndexed { index, id ->
            jdbc.update("INSERT INTO pathway_prerequisites (pathway_id, achievement_id, position) VALUES (?, ?, ?)", pathway.id, id, index)
        }
        return pathway
    }

    @Transactional
    override fun enroll(id: UUID, learnerId: UUID, email: String?): PathwayProgress {
        val created = jdbc.update("""INSERT INTO pathway_enrollments (pathway_id, learner_id)
            SELECT ?::uuid, ?::uuid WHERE NOT EXISTS (
                SELECT 1 FROM pathway_prerequisites p WHERE p.pathway_id = ? AND NOT EXISTS (
                    SELECT 1 FROM credentials c JOIN submissions s ON s.id = c.submission_id
                    WHERE c.learner_id = ? AND s.achievement_id = p.achievement_id AND c.revoked_at IS NULL
                        AND c.issued_at <= statement_timestamp() AND c.valid_until > statement_timestamp()
                )
            ) ON CONFLICT DO NOTHING""", id, learnerId, id, learnerId)
        if (created == 1 && email != null) jdbc.update("""INSERT INTO notification_outbox (id, pathway_id, learner_id, recipient, pathway_name)
            SELECT ?, id, ?, ?, name FROM pathways WHERE id = ?""", UUID.randomUUID(), learnerId, email, id)
        return progress(id, learnerId) ?: throw PrerequisitesRequired()
    }

    override fun progress(id: UUID, learnerId: UUID): PathwayProgress? {
        val rows = jdbc.query("""SELECT e.enrolled_at, r.achievement_id, EXISTS (
            SELECT 1 FROM credentials c JOIN submissions s ON s.id = c.submission_id
            WHERE c.learner_id = e.learner_id AND s.achievement_id = r.achievement_id
                AND c.revoked_at IS NULL AND c.issued_at <= statement_timestamp() AND c.valid_until > statement_timestamp()
            ) AS earned FROM pathway_enrollments e JOIN pathway_requirements r ON r.pathway_id = e.pathway_id
            WHERE e.pathway_id = ? AND e.learner_id = ? ORDER BY r.position""", { rs, _ ->
            rs.getTimestamp("enrolled_at").toInstant() to PathwayRequirement(rs.getObject("achievement_id", UUID::class.java), rs.getBoolean("earned"))
        }, id, learnerId)
        return rows.firstOrNull()?.let { PathwayProgress(id, it.first, rows.map { row -> row.second }) }
    }
}
