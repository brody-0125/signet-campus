package work.brodykim.campus.application

import java.time.Instant
import java.util.UUID

data class Pathway(val id: UUID, val name: String, val description: String, val achievementIds: List<UUID>,
                   val prerequisiteAchievementIds: List<UUID> = emptyList())
data class PathwayRequirement(val achievementId: UUID, val earned: Boolean)
data class PathwayProgress(val pathwayId: UUID, val enrolledAt: Instant, val requirements: List<PathwayRequirement>) {
    val earned: Int get() = requirements.count { it.earned }
    val total: Int get() = requirements.size
    val completed: Boolean get() = total > 0 && earned == total
}
class PathwayNotFound : RuntimeException("Pathway or enrollment not found")
class PrerequisitesRequired : RuntimeException("Current prerequisite credentials required before enrollment")

interface PathwayRepository {
    fun list(): List<Pathway>
    fun find(id: UUID): Pathway?
    fun create(pathway: Pathway): Pathway
    fun enroll(id: UUID, learnerId: UUID, email: String?): PathwayProgress
    fun progress(id: UUID, learnerId: UUID): PathwayProgress?
}

class PathwayService(private val repository: PathwayRepository) {
    fun list() = repository.list()
    fun get(id: UUID) = repository.find(id) ?: throw PathwayNotFound()
    fun create(actor: Actor, name: String, description: String, achievements: List<UUID>, prerequisites: List<UUID> = emptyList()): Pathway {
        if (!actor.reviewer) throw ReviewForbidden()
        require(name.trim().length in 1..120 && description.trim().length in 1..5000)
        require(achievements.size in 1..50 && achievements.distinct().size == achievements.size)
        require(prerequisites.size <= 50 && prerequisites.distinct().size == prerequisites.size && prerequisites.none { it in achievements })
        return repository.create(Pathway(UUID.randomUUID(), name.trim(), description.trim(), achievements, prerequisites))
    }
    fun enroll(actor: Actor, id: UUID, verifiedEmail: String? = null): PathwayProgress {
        get(id)
        require(verifiedEmail == null || (verifiedEmail.length <= 254 && verifiedEmail.matches(Regex("[^\\s@,;<>]+@[^\\s@,;<>]+"))))
        return repository.enroll(id, actor.id, verifiedEmail)
    }
    fun progress(actor: Actor, id: UUID) = repository.progress(id, actor.id) ?: throw PathwayNotFound()
}
