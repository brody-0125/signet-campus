package work.brodykim.campus.application

import java.util.UUID

interface AchievementCatalog {
    fun create(achievement: AchievementSummary): AchievementSummary
    fun update(achievement: AchievementSummary): AchievementSummary
}
class AchievementNotFound : RuntimeException("Achievement not found")
class AchievementConflict : RuntimeException("Achievement changed or already has submissions")

class AchievementCatalogService(private val catalog: AchievementCatalog) {
    fun save(actor: Actor, id: UUID?, name: String, criteria: String, expectedVersion: Long = 0): AchievementSummary {
        if (!actor.reviewer) throw ReviewForbidden()
        val achievement = AchievementSummary(id ?: UUID.randomUUID(), name.trim(), criteria.trim(), expectedVersion)
        require(achievement.name.length in 1..120 && achievement.criteria.length in 1..5000 && expectedVersion >= 0)
        return if (id == null) catalog.create(achievement) else catalog.update(achievement)
    }
}
