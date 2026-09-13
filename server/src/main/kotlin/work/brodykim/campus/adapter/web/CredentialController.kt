package work.brodykim.campus.adapter.web

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import work.brodykim.campus.application.CredentialCryptography
import work.brodykim.campus.application.Actor
import work.brodykim.campus.application.CredentialService
import java.util.UUID

@RestController
class CredentialController(private val service: CredentialService, private val crypto: CredentialCryptography) {
    @PostMapping("/api/submissions/{id}/credential")
    fun issue(authentication: JwtAuthenticationToken, @PathVariable id: UUID): ResponseEntity<String> {
        require(authentication.token.getClaim<Boolean>("email_verified") == true) { "Verified email required" }
        val email = authentication.token.getClaimAsString("email") ?: throw IllegalArgumentException("Email required")
        return document(service.issue(actor(authentication), id, email).document)
    }

    @GetMapping("/api/submissions/{id}/credential")
    fun forSubmission(authentication: JwtAuthenticationToken, @PathVariable id: UUID) =
        document(service.forSubmission(actor(authentication), id).document)

    @GetMapping("/api/credentials/{id}")
    fun download(authentication: JwtAuthenticationToken, @PathVariable id: UUID) =
        document(service.get(actor(authentication), id).document)

    @PostMapping("/api/credentials/{id}/verify")
    fun verify(@PathVariable id: UUID, @RequestBody credential: String) =
        service.verify(id, credential.also { require(it.length <= 131072) { "Credential too large" } })

    @PostMapping("/api/credentials/{id}/revoke")
    fun revoke(authentication: JwtAuthenticationToken, @PathVariable id: UUID): ResponseEntity<Void> {
        service.revoke(actor(authentication), id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/issuers/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    fun publicKey() = crypto.publicProfile()

    private fun document(json: String) = ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("application/vc+ld+json")).body(json)

    private fun actor(authentication: JwtAuthenticationToken) = Actor(UUID.fromString(authentication.token.subject),
        authentication.authorities.any { it.authority == "ROLE_REVIEWER" })
}
