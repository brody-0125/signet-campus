package work.brodykim.campus.adapter.notification

import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import work.brodykim.campus.adapter.persistence.NotificationDispatcher
import work.brodykim.campus.application.EnrollmentNotice
import work.brodykim.campus.application.NotificationSender

@Component
class SmtpNotifications(private val mail: JavaMailSender,
                        @param:Value("\${campus.public-url}") private val publicUrl: String,
                        @param:Value("\${campus.notifications.from}") private val from: String) : NotificationSender {
    override fun send(notice: EnrollmentNotice) {
        val message = mail.createMimeMessage()
        message.setFrom(InternetAddress(from, true))
        message.setRecipient(Message.RecipientType.TO, InternetAddress(notice.recipient, true))
        message.setSubject("You joined a Signet Campus pathway", "UTF-8")
        message.setText("You are enrolled in ${notice.pathwayName}.\n\nOpen Pathways to view your requirements and progress:\n${publicUrl.trimEnd('/')}\n\nCurrent badges count toward completion. Expired or revoked badges no longer count.", "UTF-8")
        message.saveChanges()
        message.setHeader("Message-ID", "<${notice.id}@signet-campus.invalid>")
        mail.send(message)
    }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["campus.notifications.enabled"], havingValue = "true")
class NotificationSchedule(private val dispatcher: NotificationDispatcher) {
    @Scheduled(fixedDelayString = "\${campus.notifications.poll-ms:2000}")
    fun deliver() { repeat(10) { if (!dispatcher.dispatchOne()) return } }
}
