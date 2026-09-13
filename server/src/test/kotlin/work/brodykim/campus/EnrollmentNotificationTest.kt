package work.brodykim.campus

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import work.brodykim.campus.application.*
import java.util.UUID

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
class EnrollmentNotificationTest : SigningTestSupport() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var pathways: PathwayService
    private fun pathway() = pathways.create(Actor(UUID.randomUUID(), true), "Accessible campus", "Learn accessible content",
        listOf(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))).id

    @Test fun `verified recipient is queued once and comes only from the authenticated account`() {
        val path = pathway()
        val learner = UUID.randomUUID()
        repeat(2) {
            mvc.perform(post("/api/pathways/$path/enrollment").with(jwt().jwt {
                it.subject(learner.toString()).claim("email", "learner@example.test").claim("email_verified", true)
            }).contentType("application/json").content("""{"email":"attacker@example.test"}"""))
                .andExpect(status().isOk)
        }
        val rows = jdbc.queryForList("SELECT recipient FROM notification_outbox WHERE pathway_id = ? AND learner_id = ?", path, learner)
        assertEquals(1, rows.size)
        assertEquals("learner@example.test", rows.single()["recipient"])
    }

    @Test fun `unverified accounts can enroll without sending an email`() {
        val path = pathway()
        val learner = UUID.randomUUID()
        mvc.perform(post("/api/pathways/$path/enrollment").with(jwt().jwt {
            it.subject(learner.toString()).claim("email", "unverified@example.test").claim("email_verified", false)
        })).andExpect(status().isOk)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE pathway_id = ? AND learner_id = ?",
            Int::class.java, path, learner)!!)
        assertNotNull(pathways.progress(Actor(learner, false), path))
    }
}
