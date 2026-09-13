package work.brodykim.campus.application

import java.time.Instant
import java.util.UUID

data class Pathway(val id: UUID, val name: String, val description: String, val achievementIds: List<UUID>)
data class PathwayRequirement(val achievementId: UUID, val earned: Boolean)
data class PathwayProgress(val pathwayId: UUID, val enrolledAt: Instant, val requirements: List<PathwayRequirement>) {
    val earned: Int get() = requirements.count { it.earned }
    val total: Int get() = requirements.size
    val completed: Boolean get() = total > 0 && earned == total
}
class PathwayNotFound : RuntimeException("Pathway or enrollment not found")

interface PathwayRepository {
    fun list(): List<Pathway>
    fun find(id: UUID): Pathway?
    fun create(pathway: Pathway): Pathway
    fun enroll(id: UUID, learnerId: UUID): PathwayProgress
    fun progress(id: UUID, learnerId: UUID): PathwayProgress?
}

class PathwayService(private val repository: PathwayRepository) {
    fun list() = repository.list()
    fun get(id: UUID) = repository.find(id) ?: throw PathwayNotFound()
    fun create(actor: Actor, name: String, description: String, achievements: List<UUID>): Pathway {
        if (!actor.reviewer) throw ReviewForbidden()
        require(name.trim().length in 1..120 && description.trim().length in 1..5000)
        require(achievements.size in 1..50 && achievements.distinct().size == achievements.size)
        return repository.create(Pathway(UUID.randomUUID(), name.trim(), description.trim(), achievements))
    }
    fun enroll(actor: Actor, id: UUID): PathwayProgress { get(id); return repository.enroll(id, actor.id) }
    fun progress(actor: Actor, id: UUID) = repository.progress(id, actor.id) ?: throw PathwayNotFound()
}
