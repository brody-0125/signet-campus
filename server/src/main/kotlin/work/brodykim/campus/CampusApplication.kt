package work.brodykim.campus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import work.brodykim.campus.application.SubmissionRepository
import work.brodykim.campus.application.SubmissionService
import work.brodykim.campus.application.CredentialService
import work.brodykim.campus.application.CredentialRepository
import work.brodykim.campus.application.CredentialCryptography
import work.brodykim.campus.application.AchievementCatalog
import work.brodykim.campus.application.AchievementCatalogService
import java.time.Clock

@SpringBootApplication
class CampusApplication {
    @Bean fun pathwayCredentialService(pathways: work.brodykim.campus.application.PathwayRepository,
        credentials: CredentialRepository, crypto: CredentialCryptography) =
        work.brodykim.campus.application.PathwayCredentialService(pathways, credentials, crypto, Clock.systemUTC())
    @Bean fun pathwayService(repository: work.brodykim.campus.application.PathwayRepository) = work.brodykim.campus.application.PathwayService(repository)
    @Bean fun achievementCatalogService(catalog: AchievementCatalog) = AchievementCatalogService(catalog)
    @Bean fun submissionService(repository: SubmissionRepository) = SubmissionService(repository, Clock.systemUTC())
    @Bean fun credentialService(submissions: SubmissionRepository, credentials: CredentialRepository, crypto: CredentialCryptography) =
        CredentialService(submissions, credentials, crypto, Clock.systemUTC())
}

fun main(args: Array<String>) { runApplication<CampusApplication>(*args) }
