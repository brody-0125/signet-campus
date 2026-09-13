package work.brodykim.campus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import work.brodykim.campus.application.SubmissionRepository
import work.brodykim.campus.application.SubmissionService
import java.time.Clock

@SpringBootApplication
class CampusApplication {
    @Bean fun submissionService(repository: SubmissionRepository) = SubmissionService(repository, Clock.systemUTC())
}

fun main(args: Array<String>) { runApplication<CampusApplication>(*args) }
