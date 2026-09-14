package work.brodykim.campus.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import work.brodykim.campus.application.AchievementUnavailable
import java.util.UUID

// Call inside the write transaction so archive/restore cannot race with admission.
internal fun JdbcTemplate.requireActiveAchievement(id: UUID) {
    val state = queryForList("SELECT published, archived FROM achievements WHERE id = ? FOR SHARE", id).singleOrNull()
        ?: throw IllegalArgumentException("Published achievement required")
    require(state["published"] == true) { "Published achievement required" }
    if (state["archived"] == true) throw AchievementUnavailable()
}
