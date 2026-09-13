package work.brodykim.campus

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import work.brodykim.campus.adapter.credential.SignetCredentials
import work.brodykim.campus.application.AchievementSummary
import work.brodykim.signet.autoconfigure.OpenBadgesAutoConfiguration
import work.brodykim.signet.credential.KeyPairManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class SigningKeyRotationTest {
    @TempDir lateinit var directory: Path
    private fun context(active: OctetKeyPair, history: String) = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java, OpenBadgesAutoConfiguration::class.java))
        .withBean(SignetCredentials::class.java)
        .withPropertyValues(
            "campus.public-url=https://campus.example",
            "campus.signing-key=${Files.writeString(directory.resolve("active.jwk"), active.toJSONString()).toUri()}",
            "campus.verification-keys=${Files.writeString(directory.resolve("public.jwks"), history).toUri()}")
    private fun issue(crypto: SignetCredentials) = crypto.issue(UUID.randomUUID(), "learner@example.test",
        AchievementSummary(UUID.randomUUID(), "Accessibility", "Complete audit"),
        Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2027-09-14T00:00:00Z"))

    @Test fun `retained public key verifies old credentials while new key signs after rotation`() {
        val old = KeyPairManager.generateEd25519KeyPair()
        val next = KeyPairManager.generateEd25519KeyPair()
        var oldDocument = ""
        context(old, "{\"keys\":[]}").run { oldDocument = issue(it.getBean(SignetCredentials::class.java)) }
        context(next, JWKSet(listOf(old.toPublicJWK(), next.toPublicJWK())).toString()).run {
            assertNull(it.startupFailure)
            val crypto = it.getBean(SignetCredentials::class.java)
            assertTrue(crypto.verify(oldDocument), "Old public key must remain usable after rotation")
            val newDocument = issue(crypto)
            assertTrue(crypto.verify(newDocument))
            assertTrue(newDocument.contains(next.computeThumbprint().toString()))
            val profile = crypto.publicProfile()
            val methods = profile["verificationMethod"] as List<*>
            assertEquals(2, methods.size)
            assertEquals(2, (profile["assertionMethod"] as List<*>).size)
            assertFalse(profile.toString().contains(old.d.toString()))
            assertFalse(profile.toString().contains(next.d.toString()))
            assertFalse(crypto.verify(oldDocument.replace(old.computeThumbprint().toString(), "unknown-key")))
        }
        context(next, "{\"keys\":[]}").run {
            assertFalse(it.getBean(SignetCredentials::class.java).verify(oldDocument), "Removed keys must no longer be trusted")
        }
    }

    @Test fun `private and malformed verification key material prevents startup`() {
        val key = KeyPairManager.generateEd25519KeyPair()
        val wrongCurve = OctetKeyPair.Builder(Curve.X25519, key.x).build()
        for (material in listOf(JWKSet(key).toString(false), "not-json", JWKSet(wrongCurve).toString())) {
            context(key, material).run { assertNotNull(it.startupFailure) }
        }
    }
}
