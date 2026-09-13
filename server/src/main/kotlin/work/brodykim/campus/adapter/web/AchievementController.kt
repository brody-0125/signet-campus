package work.brodykim.campus.adapter.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import work.brodykim.campus.application.SubmissionService

@RestController
class AchievementController(private val service: SubmissionService) {
    @GetMapping("/api/achievements")
    fun list() = service.achievements()
}
