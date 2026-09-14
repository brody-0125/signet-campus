package work.brodykim.campus.adapter.web

import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import work.brodykim.campus.application.AchievementCatalogService
import work.brodykim.campus.application.Actor
import work.brodykim.campus.application.AchievementNotFound
import java.net.URI
import java.util.UUID
import work.brodykim.campus.application.SubmissionService

data class AchievementInput(val name: String, val criteria: String, val expectedVersion: Long = 0)
data class PublicationInput(val expectedVersion: Long)
data class ArchivalInput(val expectedVersion: Long, val archived: Boolean? = null)

@RestController
class AchievementController(private val service: SubmissionService, private val catalog: AchievementCatalogService) {
    @GetMapping("/api/achievements")
    fun list(@RequestParam(defaultValue = "false") includeArchived: Boolean) = service.achievements(includeArchived)

    @GetMapping("/api/reviewer/achievements")
    fun manage(authentication: JwtAuthenticationToken) = ResponseEntity.ok().header("Cache-Control", "no-store")
        .body(catalog.list(actor(authentication)))

    @GetMapping("/api/reviewer/achievements/{id}")
    fun draft(authentication: JwtAuthenticationToken, @PathVariable id: UUID) = ResponseEntity.ok().header("Cache-Control", "no-store")
        .body(catalog.list(actor(authentication)).firstOrNull { it.id == id } ?: throw AchievementNotFound())

    @GetMapping("/api/achievements/{id}")
    fun get(@PathVariable id: UUID) = service.achievements(includeArchived = true).firstOrNull { it.id == id } ?: throw AchievementNotFound()

    @PostMapping("/api/achievements")
    fun create(authentication: JwtAuthenticationToken, @RequestBody input: AchievementInput) =
        catalog.save(actor(authentication), null, input.name, input.criteria).let {
            ResponseEntity.created(URI("/api/reviewer/achievements/${it.id}")).header("Cache-Control", "no-store").body(it)
        }

    @PostMapping("/api/achievements/{id}")
    fun update(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody input: AchievementInput) =
        catalog.save(actor(authentication), id, input.name, input.criteria, input.expectedVersion)

    @PostMapping("/api/achievements/{id}/publish")
    fun publish(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody input: PublicationInput) =
        catalog.publish(actor(authentication), id, input.expectedVersion)

    @PostMapping("/api/achievements/{id}/archive")
    fun archive(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody input: ArchivalInput) =
        catalog.archive(actor(authentication), id, input.expectedVersion, requireNotNull(input.archived))

    @PostMapping("/api/achievements/{id}/successors")
    fun successor(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody input: AchievementInput) =
        catalog.successor(actor(authentication), id, input.expectedVersion, input.name, input.criteria).let {
            ResponseEntity.created(URI("/api/reviewer/achievements/${it.id}")).header("Cache-Control", "no-store").body(it)
        }

    private fun actor(authentication: JwtAuthenticationToken) = Actor(UUID.fromString(authentication.token.subject),
        authentication.authorities.any { it.authority == "ROLE_REVIEWER" })
}
