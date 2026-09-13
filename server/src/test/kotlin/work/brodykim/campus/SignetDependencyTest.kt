package work.brodykim.campus

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import work.brodykim.signet.autoconfigure.OpenBadgesAutoConfiguration
import work.brodykim.signet.core.BadgeAchievement
import work.brodykim.signet.core.BadgeIssuer
import work.brodykim.signet.credential.CredentialBuilder
import work.brodykim.signet.credential.CredentialSigner
import work.brodykim.signet.credential.KeyPairManager
import java.util.UUID

/** LIB-01: use only published artifacts; no local repository or source substitution. */
class SignetDependencyTest : StringSpec({
    "published starter configures signing beans and rejects tampered credentials" {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                JacksonAutoConfiguration::class.java,
                OpenBadgesAutoConfiguration::class.java,
            ))
            .run { context ->
                context.startupFailure shouldBe null
                val builder = context.getBean(CredentialBuilder::class.java)
                val signer = context.getBean(CredentialSigner::class.java)
                val key = KeyPairManager.generateEd25519KeyPair()
                val achievement = BadgeAchievement(UUID.randomUUID(), "Accessibility awareness",
                    "Identify barriers in digital content", "Complete the accessibility assessment",
                    "Badge", null, listOf("accessibility"))
                val issuer = BadgeIssuer(UUID.randomUUID(), "Signet Campus",
                    "https://campus.example", null, "Training provider")
                val credential = builder.buildCredential(UUID.randomUUID(), "learner@example.test",
                    null, achievement, issuer)
                val signed = signer.signWithDataIntegrity(credential, key, "https://campus.example/keys/1")
                signer.verifyDataIntegrity(signed, key.toPublicJWK()) shouldBe true
                signer.verifyDataIntegrity(signed, KeyPairManager.generateEd25519KeyPair().toPublicJWK()) shouldBe false
                val tampered = signed.toMutableMap().apply { put("name", "Unassessed achievement") }
                signer.verifyDataIntegrity(tampered, key.toPublicJWK()) shouldBe false
                // Exercise consumer Jackson linkage through the published dependency graph.
                context.getBean(ObjectMapper::class.java).readTree(
                    context.getBean(ObjectMapper::class.java).writeValueAsString(signed)
                )["type"].size() shouldBe 2
            }
    }
})
