package work.brodykim.campus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import work.brodykim.signet.core.BadgeAchievement
import work.brodykim.signet.core.BadgeIssuer
import work.brodykim.signet.credential.CredentialBuilder
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Isolated("Checks published dependency behavior under a different JVM locale")
class RecipientDependencyTest {
    @Test fun `published core keeps recipient identity stable across deployment locales`() {
        val original = Locale.getDefault()
        try {
            val builder = CredentialBuilder("https://campus.example", "contract-salt")
            val id = UUID.randomUUID()
            val issuer = BadgeIssuer(UUID.randomUUID(), "Campus", "https://campus.example", null, null)
            val achievement = BadgeAchievement(UUID.randomUUID(), "Accessibility", "Audit", "Complete an audit", "Badge", null, emptyList())
            val at = Instant.parse("2026-09-14T00:00:00Z")
            Locale.setDefault(Locale.ROOT)
            val expected = builder.buildCredential(id, "ivan@example.test", null, achievement, issuer, at)
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(expected, builder.buildCredential(id, " IVAN@EXAMPLE.TEST ", null, achievement, issuer, at))
        } finally { Locale.setDefault(original) }
    }
}
