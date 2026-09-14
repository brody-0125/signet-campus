package work.brodykim.campus.adapter.web

import org.springframework.http.CacheControl
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import work.brodykim.campus.application.CredentialCryptography
import work.brodykim.campus.application.Actor
import work.brodykim.campus.application.CredentialService
import java.util.UUID

data class SharingInput(val enabled: Boolean)
data class SharingStatus(val enabled: Boolean, val url: String?)

@RestController
class CredentialController(private val service: CredentialService, private val crypto: CredentialCryptography,
                           private val pathways: work.brodykim.campus.application.PathwayCredentialService,
                           private val images: work.brodykim.campus.application.CredentialImages,
                           @param:Value("\${campus.public-url}") private val publicUrl: String) {
    @GetMapping("/api/shared/credentials/{id}")
    fun shared(@PathVariable id: UUID): ResponseEntity<String> = service.shared(id)?.let { document(it.document) }
        ?: ResponseEntity.status(404).cacheControl(CacheControl.noStore()).build()

    @GetMapping("/api/credentials/{id}/sharing")
    fun sharing(authentication: JwtAuthenticationToken, @PathVariable id: UUID) =
        sharingStatus(id, service.get(actor(authentication), id).shared)

    @PostMapping("/api/credentials/{id}/sharing")
    fun setSharing(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody input: SharingInput): ResponseEntity<SharingStatus> {
        service.setSharing(actor(authentication), id, input.enabled)
        return sharingStatus(id, input.enabled)
    }

    private fun sharingStatus(id: UUID, enabled: Boolean) = ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(SharingStatus(enabled, if (enabled) "${publicUrl.trimEnd('/')}/shared/$id" else null))

    @GetMapping("/api/credentials/{id}/image/{format}")
    fun image(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @PathVariable format: String): ResponseEntity<ByteArray> {
        val record = service.get(actor(authentication), id)
        val imageFormat = when (format) {
            "png" -> work.brodykim.campus.application.BadgeImageFormat.PNG
            "svg" -> work.brodykim.campus.application.BadgeImageFormat.SVG
            else -> throw IllegalArgumentException("Supported formats: png, svg")
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Content-Disposition", "attachment; filename=\"signet-campus-$id.$format\"")
            .contentType(MediaType.parseMediaType(if (format == "png") "image/png" else "image/svg+xml"))
            .body(images.export(record.document, imageFormat))
    }

    @PostMapping("/api/pathways/{id}/credential")
    fun issuePathway(authentication: JwtAuthenticationToken, @PathVariable id: UUID): ResponseEntity<String> {
        require(authentication.token.getClaim<Boolean>("email_verified") == true) { "Verified email required" }
        val email = authentication.token.getClaimAsString("email") ?: throw IllegalArgumentException("Email required")
        return document(pathways.issue(actor(authentication), id, email).document)
    }

    @GetMapping("/api/pathways/{id}/credential")
    fun pathwayCredential(authentication: JwtAuthenticationToken, @PathVariable id: UUID) =
        document(pathways.get(actor(authentication), id).document)

    @GetMapping("/api/revocations", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun revocations() = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapOf(
        "id" to "${publicUrl.trimEnd('/')}/api/revocations",
        "issuer" to crypto.publicProfile().getValue("id"),
        "revokedCredentials" to service.revokedCredentialIds().map { mapOf("id" to it, "revoked" to true) },
    ))

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
