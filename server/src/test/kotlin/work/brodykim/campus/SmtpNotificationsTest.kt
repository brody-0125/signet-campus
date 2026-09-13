package work.brodykim.campus

import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSenderImpl
import work.brodykim.campus.adapter.notification.SmtpNotifications
import work.brodykim.campus.application.EnrollmentNotice
import java.util.UUID

class SmtpNotificationsTest {
    @Test fun `mail uses a stable message id plain text and the configured campus URL`() {
        val delivered = mutableListOf<MimeMessage>()
        val sender = object : JavaMailSenderImpl() {
            override fun send(vararg mimeMessages: MimeMessage) { delivered.addAll(mimeMessages) }
        }
        val notice = EnrollmentNotice(UUID.randomUUID(), "learner@example.test", "Accessible campus")
        val smtp = SmtpNotifications(sender, "https://campus.example.test/", "campus@example.test")
        repeat(2) { smtp.send(notice) }
        assertEquals("<${notice.id}@signet-campus.invalid>", delivered[0].messageID)
        assertEquals(delivered[0].messageID, delivered[1].messageID)
        assertTrue(delivered[0].content.toString().contains("https://campus.example.test"))
        assertTrue(delivered[0].content.toString().contains(notice.pathwayName))
        assertEquals("learner@example.test", delivered[0].allRecipients.single().toString())
    }
}
