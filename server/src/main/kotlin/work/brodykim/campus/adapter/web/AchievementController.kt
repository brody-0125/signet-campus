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

@RestController
class AchievementController(private val service: SubmissionService, private val catalog: AchievementCatalogService) {
    @GetMapping("/api/achievements")
    fun list() = service.achievements()

    @GetMapping("/api/achievements/{id}")
    fun get(@PathVariable id: UUID) = service.achievements().firstOrNull { it.id == id } ?: throw AchievementNotFound()

    @PostMapping("/api/achievements")
    fun create(authentication: JwtAuthenticationToken, @RequestBody input: AchievementInput) =
        catalog.save(actor(authentication), null, input.name, input.criteria).let {
            ResponseEntity.created(URI("/api/achievements/${it.id}")).body(it)
        }

    @PostMapping("/api/achievements/{id}")
    fun update(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody input: AchievementInput) =
        catalog.save(actor(authentication), id, input.name, input.criteria, input.expectedVersion)

    private fun actor(authentication: JwtAuthenticationToken) = Actor(UUID.fromString(authentication.token.subject),
        authentication.authorities.any { it.authority == "ROLE_REVIEWER" })
}
