package work.brodykim.campus.adapter.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import work.brodykim.campus.application.EnrollmentNotice
import work.brodykim.campus.application.NotificationSender
import java.util.UUID

@Component
class NotificationDispatcher(private val jdbc: JdbcTemplate, transactions: PlatformTransactionManager,
                             private val sender: NotificationSender) {
    private val transaction = TransactionTemplate(transactions)
    private val logger = KotlinLogging.logger {}

    // SMTP is bounded by transport timeouts. Each transaction locks one event, allowing other workers to skip it.
    fun dispatchOne(): Boolean = transaction.execute {
        val notice = jdbc.query("""SELECT * FROM notification_outbox WHERE sent_at IS NULL AND next_attempt_at <= now()
            ORDER BY next_attempt_at, id LIMIT 1 FOR UPDATE SKIP LOCKED""", { rs, _ ->
            EnrollmentNotice(rs.getObject("id", UUID::class.java),
                rs.getString("recipient"), rs.getString("pathway_name"))
        }).singleOrNull() ?: return@execute false
        try {
            sender.send(notice)
        } catch (failure: Exception) {
            jdbc.update("""UPDATE notification_outbox SET attempts = attempts + 1, last_error = ?,
                next_attempt_at = now() + make_interval(secs => LEAST(3600, 5 * power(2, LEAST(attempts, 10)))::int)
                WHERE id = ?""", failure.javaClass.simpleName, notice.id)
            logger.warn { "Notification ${notice.id} deferred: ${failure.javaClass.simpleName}" }
            return@execute true
        }
        jdbc.update("UPDATE notification_outbox SET sent_at = now(), recipient = NULL, attempts = attempts + 1, last_error = NULL WHERE id = ?", notice.id)
        true
    } ?: false
}
