package work.brodykim.campus.application

import java.util.UUID

interface AchievementCatalog {
    fun list(): List<AchievementSummary>
    fun create(achievement: AchievementSummary): AchievementSummary
    fun update(achievement: AchievementSummary): AchievementSummary
    fun publish(id: UUID, expectedVersion: Long): AchievementSummary
    fun archive(id: UUID, expectedVersion: Long, archived: Boolean): AchievementSummary
}
class AchievementUnavailable : RuntimeException("Achievement is archived; new work is paused until the issuer restores it")
class AchievementNotFound : RuntimeException("Achievement not found")
class AchievementConflict : RuntimeException("Achievement changed or already has submissions")

class AchievementCatalogService(private val catalog: AchievementCatalog) {
    fun archive(actor: Actor, id: UUID, expectedVersion: Long, archived: Boolean): AchievementSummary {
        if (!actor.reviewer) throw ReviewForbidden()
        require(expectedVersion >= 0)
        return catalog.archive(id, expectedVersion, archived)
    }
    fun list(actor: Actor): List<AchievementSummary> {
        if (!actor.reviewer) throw ReviewForbidden()
        return catalog.list()
    }
    fun publish(actor: Actor, id: UUID, expectedVersion: Long): AchievementSummary {
        if (!actor.reviewer) throw ReviewForbidden()
        require(expectedVersion >= 0)
        return catalog.publish(id, expectedVersion)
    }
    fun save(actor: Actor, id: UUID?, name: String, criteria: String, expectedVersion: Long = 0): AchievementSummary {
        if (!actor.reviewer) throw ReviewForbidden()
        val achievement = AchievementSummary(id ?: UUID.randomUUID(), name.trim(), criteria.trim(), expectedVersion)
        require(achievement.name.length in 1..120 && achievement.criteria.length in 1..5000 && expectedVersion >= 0)
        return if (id == null) catalog.create(achievement) else catalog.update(achievement)
    }
}
