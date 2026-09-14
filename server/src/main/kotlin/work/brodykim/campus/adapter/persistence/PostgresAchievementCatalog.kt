package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import work.brodykim.campus.application.*
import java.util.UUID

@Repository
class PostgresAchievementCatalog(private val jdbc: JdbcTemplate) : AchievementCatalog {
    override fun list(): List<AchievementSummary> = jdbc.query("SELECT id, name, criteria, version, published, archived, predecessor_id FROM achievements ORDER BY name, id") { rs, _ ->
        AchievementSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("criteria"), rs.getLong("version"), rs.getBoolean("published"), rs.getBoolean("archived"), rs.getObject("predecessor_id", UUID::class.java))
    }
    @Transactional
    override fun successor(sourceId: UUID, expectedVersion: Long, draft: AchievementSummary): AchievementSummary {
        val source = jdbc.queryForList("SELECT version, published FROM achievements WHERE id = ? FOR SHARE", sourceId)
            .singleOrNull() ?: throw AchievementNotFound()
        if (source["version"] != expectedVersion || source["published"] != true) throw AchievementConflict()
        jdbc.update("INSERT INTO achievements (id, name, criteria, predecessor_id) VALUES (?, ?, ?, ?)",
            draft.id, draft.name, draft.criteria, sourceId)
        return draft
    }
    override fun create(achievement: AchievementSummary): AchievementSummary {
        jdbc.update("INSERT INTO achievements (id, name, criteria) VALUES (?, ?, ?)", achievement.id, achievement.name, achievement.criteria)
        return achievement.copy(version = 0)
    }

    @Transactional
    override fun update(achievement: AchievementSummary): AchievementSummary {
        val current = jdbc.queryForList("SELECT version, published, archived, predecessor_id FROM achievements WHERE id = ? FOR UPDATE", achievement.id)
            .singleOrNull() ?: throw AchievementNotFound()
        if (current["version"] != achievement.version || current["archived"] == true || jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM submissions WHERE achievement_id = ?)",
                Boolean::class.java, achievement.id) == true) throw AchievementConflict()
        jdbc.update("UPDATE achievements SET name = ?, criteria = ?, version = version + 1 WHERE id = ?",
            achievement.name, achievement.criteria, achievement.id)
        return achievement.copy(version = achievement.version + 1, published = current["published"] as Boolean, predecessorId = current["predecessor_id"] as UUID?)
    }

    @Transactional
    override fun publish(id: UUID, expectedVersion: Long): AchievementSummary {
        val current = jdbc.queryForList("SELECT version, published FROM achievements WHERE id = ? FOR UPDATE", id)
            .singleOrNull() ?: throw AchievementNotFound()
        if (current["version"] != expectedVersion || current["published"] == true) throw AchievementConflict()
        return jdbc.query("UPDATE achievements SET published = true, version = version + 1 WHERE id = ? RETURNING id, name, criteria, version, predecessor_id",
            { rs, _ -> AchievementSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("criteria"), rs.getLong("version"), true, predecessorId = rs.getObject("predecessor_id", UUID::class.java)) }, id).single()
    }

    @Transactional
    override fun archive(id: UUID, expectedVersion: Long, archived: Boolean): AchievementSummary {
        val current = jdbc.queryForList("SELECT version, published, archived, predecessor_id FROM achievements WHERE id = ? FOR UPDATE", id)
            .singleOrNull() ?: throw AchievementNotFound()
        if (current["version"] != expectedVersion || current["published"] != true || current["archived"] == archived) throw AchievementConflict()
        return jdbc.query("UPDATE achievements SET archived = ?, version = version + 1 WHERE id = ? RETURNING id, name, criteria, version, predecessor_id",
            { rs, _ -> AchievementSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("criteria"), rs.getLong("version"), true, archived, rs.getObject("predecessor_id", UUID::class.java)) }, archived, id).single()
    }
}
