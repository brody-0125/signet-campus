package work.brodykim.campus

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import work.brodykim.campus.adapter.persistence.NotificationDispatcher
import work.brodykim.campus.application.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Tag("postgres")
@SpringBootTest
class NotificationDispatchTest : SigningTestSupport() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var transactions: PlatformTransactionManager
    @Autowired lateinit var pathways: PathwayService
    private val learner = Actor(UUID.randomUUID(), false)
    private fun pathway() = pathways.create(Actor(UUID.randomUUID(), true), "Accessible campus", "Learn accessible content",
        listOf(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))).id
    @BeforeEach @AfterEach fun clean() {
        check(jdbc.queryForObject("SELECT current_database()", String::class.java) == "campus_test")
        jdbc.execute("TRUNCATE notification_outbox")
    }
    private fun worker(send: (EnrollmentNotice) -> Unit) = NotificationDispatcher(jdbc, transactions, NotificationSender(send))

    @Test fun `rollback leaves neither enrollment nor notification`() {
        val path = pathway()
        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactions).executeWithoutResult {
                pathways.enroll(learner, path, "learner@example.test")
                error("Force transaction rollback")
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pathway_enrollments WHERE pathway_id = ?", Int::class.java, path)!!)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Int::class.java)!!)
    }

    @Test fun `failed delivery backs off then retries the same notice and removes the recipient on success`() {
        pathways.enroll(learner, pathway(), "learner@example.test")
        val notices = mutableListOf<UUID>()
        assertTrue(worker { notices.add(it.id); throw IllegalStateException("sensitive SMTP error") }.dispatchOne())
        val failed = jdbc.queryForMap("SELECT * FROM notification_outbox")
        assertEquals(1, failed["attempts"])
        assertEquals("IllegalStateException", failed["last_error"])
        assertFalse(worker { error("Backoff must suppress delivery") }.dispatchOne())
        jdbc.update("UPDATE notification_outbox SET next_attempt_at = now() - interval '1 second'")
        assertTrue(worker { notices.add(it.id) }.dispatchOne())
        assertEquals(notices[0], notices[1])
        val sent = jdbc.queryForMap("SELECT * FROM notification_outbox")
        assertNotNull(sent["sent_at"])
        assertNull(sent["recipient"])
        assertNull(sent["last_error"])
        assertEquals(2, sent["attempts"])
        assertFalse(worker { error("Already delivered") }.dispatchOne())
    }

    @Test fun `concurrent workers skip a claimed notice instead of sending it twice`() {
        val path = pathway()
        val pool = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val enrollments = pool.invokeAll(List(2) { java.util.concurrent.Callable { pathways.enroll(learner, path, "learner@example.test") } }).map { it.get() }
            assertEquals(enrollments[0], enrollments[1])
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Int::class.java)!!)
            val delivering = pool.submit<Boolean> { worker { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }.dispatchOne() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(worker { error("Another worker owns this notice") }.dispatchOne())
            release.countDown()
            assertTrue(delivering.get(5, TimeUnit.SECONDS))
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
