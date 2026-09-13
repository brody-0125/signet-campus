package work.brodykim.campus.adapter.web

import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import work.brodykim.campus.application.Actor
import work.brodykim.campus.application.SubmissionService
import java.util.UUID

data class SubmitEvidence(val achievementId: UUID, val evidence: String)
data class ReviewRequest(val expectedVersion: Long)
data class RejectRequest(val expectedVersion: Long, val reason: String)
data class ResubmitRequest(val expectedVersion: Long, val evidence: String)

@RestController
@RequestMapping("/api/submissions")
class SubmissionController(private val service: SubmissionService) {
    @GetMapping
    fun list(authentication: JwtAuthenticationToken, @RequestParam(defaultValue = "0") offset: Int,
             @RequestParam(defaultValue = "50") limit: Int) = service.list(actor(authentication), offset, limit)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(authentication: JwtAuthenticationToken, @RequestBody request: SubmitEvidence) =
        service.submit(actor(authentication), request.achievementId, request.evidence)

    @GetMapping("/{id}")
    fun get(authentication: JwtAuthenticationToken, @PathVariable id: UUID) = service.get(actor(authentication), id)

    @PostMapping("/{id}/approve")
    fun approve(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody request: ReviewRequest) =
        service.approve(actor(authentication), id, request.expectedVersion)

    @PostMapping("/{id}/reject")
    fun reject(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody request: RejectRequest) =
        service.reject(actor(authentication), id, request.expectedVersion, request.reason)

    @PostMapping("/{id}/resubmit")
    fun resubmit(authentication: JwtAuthenticationToken, @PathVariable id: UUID, @RequestBody request: ResubmitRequest) =
        service.resubmit(actor(authentication), id, request.expectedVersion, request.evidence)

    private fun actor(authentication: JwtAuthenticationToken) = Actor(UUID.fromString(authentication.token.subject),
        authentication.authorities.any { it.authority == "ROLE_REVIEWER" })
}
