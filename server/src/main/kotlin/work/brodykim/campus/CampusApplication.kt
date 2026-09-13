package work.brodykim.campus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import work.brodykim.campus.application.SubmissionRepository
import work.brodykim.campus.application.SubmissionService
import work.brodykim.campus.application.CredentialService
import work.brodykim.campus.application.CredentialRepository
import work.brodykim.campus.application.CredentialCryptography
import java.time.Clock

@SpringBootApplication
class CampusApplication {
    @Bean fun submissionService(repository: SubmissionRepository) = SubmissionService(repository, Clock.systemUTC())
    @Bean fun credentialService(submissions: SubmissionRepository, credentials: CredentialRepository, crypto: CredentialCryptography) =
        CredentialService(submissions, credentials, crypto, Clock.systemUTC())
}

fun main(args: Array<String>) { runApplication<CampusApplication>(*args) }
