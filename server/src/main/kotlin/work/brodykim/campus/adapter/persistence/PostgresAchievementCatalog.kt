package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import work.brodykim.campus.application.*

@Repository
class PostgresAchievementCatalog(private val jdbc: JdbcTemplate) : AchievementCatalog {
    override fun create(achievement: AchievementSummary): AchievementSummary {
        jdbc.update("INSERT INTO achievements (id, name, criteria) VALUES (?, ?, ?)", achievement.id, achievement.name, achievement.criteria)
        return achievement.copy(version = 0)
    }

    @Transactional
    override fun update(achievement: AchievementSummary): AchievementSummary {
        val current = jdbc.queryForList("SELECT version FROM achievements WHERE id = ? FOR UPDATE", Long::class.java, achievement.id)
            .singleOrNull() ?: throw AchievementNotFound()
        if (current != achievement.version || jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM submissions WHERE achievement_id = ?)",
                Boolean::class.java, achievement.id) == true) throw AchievementConflict()
        jdbc.update("UPDATE achievements SET name = ?, criteria = ?, version = version + 1 WHERE id = ?",
            achievement.name, achievement.criteria, achievement.id)
        return achievement.copy(version = current + 1)
    }
}
