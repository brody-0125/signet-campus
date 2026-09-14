package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import work.brodykim.campus.application.*
import java.util.UUID

@Repository
class PostgresAchievementCatalog(private val jdbc: JdbcTemplate) : AchievementCatalog {
    override fun list(): List<AchievementSummary> = jdbc.query("SELECT id, name, criteria, version, published FROM achievements ORDER BY name, id") { rs, _ ->
        AchievementSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("criteria"), rs.getLong("version"), rs.getBoolean("published"))
    }
    override fun create(achievement: AchievementSummary): AchievementSummary {
        jdbc.update("INSERT INTO achievements (id, name, criteria) VALUES (?, ?, ?)", achievement.id, achievement.name, achievement.criteria)
        return achievement.copy(version = 0)
    }

    @Transactional
    override fun update(achievement: AchievementSummary): AchievementSummary {
        val current = jdbc.queryForList("SELECT version, published FROM achievements WHERE id = ? FOR UPDATE", achievement.id)
            .singleOrNull() ?: throw AchievementNotFound()
        if (current["version"] != achievement.version || jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM submissions WHERE achievement_id = ?)",
                Boolean::class.java, achievement.id) == true) throw AchievementConflict()
        jdbc.update("UPDATE achievements SET name = ?, criteria = ?, version = version + 1 WHERE id = ?",
            achievement.name, achievement.criteria, achievement.id)
        return achievement.copy(version = achievement.version + 1, published = current["published"] as Boolean)
    }

    @Transactional
    override fun publish(id: UUID, expectedVersion: Long): AchievementSummary {
        val current = jdbc.queryForList("SELECT version, published FROM achievements WHERE id = ? FOR UPDATE", id)
            .singleOrNull() ?: throw AchievementNotFound()
        if (current["version"] != expectedVersion || current["published"] == true) throw AchievementConflict()
        return jdbc.query("UPDATE achievements SET published = true, version = version + 1 WHERE id = ? RETURNING id, name, criteria, version",
            { rs, _ -> AchievementSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("criteria"), rs.getLong("version"), true) }, id).single()
    }
}
