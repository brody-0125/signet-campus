package work.brodykim.campus.adapter.web

import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import work.brodykim.campus.application.Actor
import work.brodykim.campus.application.PathwayService
import java.net.URI
import java.util.UUID

data class PathwayInput(val name: String, val description: String, val achievementIds: List<UUID>)

@RestController
class PathwayController(private val service: PathwayService) {
    @GetMapping("/api/pathways") fun list() = service.list()
    @GetMapping("/api/pathways/{id}") fun get(@PathVariable id: UUID) = service.get(id)
    @PostMapping("/api/pathways")
    fun create(authentication: JwtAuthenticationToken, @RequestBody input: PathwayInput) =
        service.create(actor(authentication), input.name, input.description, input.achievementIds).let {
            ResponseEntity.created(URI("/api/pathways/${it.id}")).body(it)
        }
    @PostMapping("/api/pathways/{id}/enrollment")
    fun enroll(authentication: JwtAuthenticationToken, @PathVariable id: UUID) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.enroll(actor(authentication), id,
            authentication.token.getClaimAsString("email").takeIf { authentication.token.getClaimAsBoolean("email_verified") == true }))
    @GetMapping("/api/pathways/{id}/progress")
    fun progress(authentication: JwtAuthenticationToken, @PathVariable id: UUID) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.progress(actor(authentication), id))
    private fun actor(authentication: JwtAuthenticationToken) = Actor(UUID.fromString(authentication.token.subject),
        authentication.authorities.any { it.authority == "ROLE_REVIEWER" })
}
