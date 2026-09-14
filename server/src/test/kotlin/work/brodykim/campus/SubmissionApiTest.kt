package work.brodykim.campus

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import work.brodykim.campus.application.SubmissionRepository
import work.brodykim.campus.application.SubmissionConflict
import org.junit.jupiter.api.Assertions.*

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionApiTest : SigningTestSupport() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var repository: SubmissionRepository
    private val learner = "11111111-1111-4111-8111-111111111111"
    private val reviewer = "22222222-2222-4222-8222-222222222222"
    private val achievement = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private fun auth(id: String = learner, canReview: Boolean = false) = jwt()
        .jwt { it.subject(id) }
        .authorities(if (canReview) listOf(SimpleGrantedAuthority("ROLE_REVIEWER")) else emptyList())

    @BeforeEach fun reset() {
        check(jdbc.queryForObject("SELECT current_database()", String::class.java) == "campus_test") {
            "Integration tests require the isolated campus_test database"
        }
        jdbc.execute("TRUNCATE credentials, submission_audit, submissions")
    }

    private fun submit(): String = json.readTree(mvc.perform(post("/api/submissions").with(auth())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"achievementId":"$achievement","evidence":"Keyboard audit"}"""))
        .andExpect(status().isCreated).andExpect(jsonPath("$.version").value(0))
        .andReturn().response.contentAsString)["submission"]["id"].asText()

    @Test fun `unauthenticated calls and forged JWTs are rejected`() {
        mvc.perform(get("/api/achievements")).andExpect(status().isOk)
        mvc.perform(get("/api/submissions")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/achievements").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized)
    }

    @Test fun `only owner and reviewers can read private evidence`() {
        val catalog = mvc.perform(get("/api/achievements").with(auth()))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(json.readTree(catalog).any { it["id"].asText() == achievement })
        val id = submit()
        mvc.perform(get("/api/submissions/$id").with(auth())).andExpect(status().isOk)
        mvc.perform(get("/api/submissions/$id").with(auth(UUID.randomUUID().toString())))
            .andExpect(status().isNotFound)
        mvc.perform(get("/api/submissions/$id").with(auth(reviewer, true))).andExpect(status().isOk)
        mvc.perform(get("/api/submissions").with(auth())).andExpect(jsonPath("$.length()").value(1))
        mvc.perform(get("/api/submissions").with(auth(reviewer, true))).andExpect(jsonPath("$.length()").value(1))
        mvc.perform(get("/api/submissions").with(auth(UUID.randomUUID().toString())))
            .andExpect(jsonPath("$.length()").value(0))
        mvc.perform(get("/api/submissions?limit=101").with(auth())).andExpect(status().isBadRequest)
        mvc.perform(get("/api/submissions?offset=-1").with(auth())).andExpect(status().isBadRequest)
    }

    @Test fun `learners see newest submissions while reviewer pages retain FIFO and stable ties`() {
        val ids = List(3) { submit() }.sorted()
        ids.forEachIndexed { index, id ->
            jdbc.update("UPDATE submissions SET submitted_at = ?::timestamptz WHERE id = ?::uuid",
                if (index == 2) "2021-01-01T00:00:00Z" else "2020-01-01T00:00:00Z", id)
        }
        fun page(offset: Int, reviewing: Boolean): List<String> = json.readTree(mvc.perform(
            get("/api/submissions?offset=$offset&limit=2").with(if (reviewing) auth(reviewer, true) else auth()))
            .andExpect(status().isOk).andReturn().response.contentAsString).map { it["submission"]["id"].asText() }
        assertEquals(ids.reversed(), page(0, false) + page(2, false))
        assertEquals(ids, page(0, true) + page(2, true))
        mvc.perform(post("/api/submissions/${ids[2]}/approve").with(auth(reviewer, true))
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":0}"""))
            .andExpect(status().isOk)
        assertEquals(ids.take(2), page(0, true))
    }

    @Test fun `resubmission returns to the top for its learner and the back of the reviewer queue`() {
        val old = submit()
        val pending = submit()
        jdbc.update("UPDATE submissions SET submitted_at = '2020-01-01'::timestamptz")
        mvc.perform(post("/api/submissions/$old/reject").with(auth(reviewer, true))
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":0,"reason":"Add keyboard evidence"}"""))
            .andExpect(status().isOk)
        mvc.perform(post("/api/submissions/$old/resubmit").with(auth())
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":1,"evidence":"Keyboard audit with remediation"}"""))
            .andExpect(status().isOk)
        mvc.perform(get("/api/submissions?limit=1").with(auth()))
            .andExpect(jsonPath("$[0].submission.id").value(old))
        mvc.perform(get("/api/submissions?limit=1").with(auth(reviewer, true)))
            .andExpect(jsonPath("$[0].submission.id").value(pending))
    }

    @Test fun `only reviewers may approve and stale versions conflict`() {
        val id = submit()
        val body = """{"expectedVersion":0}"""
        mvc.perform(post("/api/submissions/$id/approve").with(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc.perform(post("/api/submissions/$id/approve").with(auth(reviewer, true)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.submission.status").value("APPROVED"))
            .andExpect(jsonPath("$.version").value(1))
        mvc.perform(post("/api/submissions/$id/reject").with(auth(reviewer, true))
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":0,"reason":"Changed mind"}"""))
            .andExpect(status().isConflict)
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM submission_audit", Int::class.java)!!)
    }

    @Test fun `rejected evidence can be resubmitted and remains pending after reload`() {
        val id = submit()
        mvc.perform(post("/api/submissions/$id/reject").with(auth(reviewer, true))
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":0,"reason":"Add screenshots"}"""))
            .andExpect(status().isOk)
        mvc.perform(post("/api/submissions/$id/resubmit").with(auth())
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":1,"evidence":"Audit with screenshots"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(2))
        mvc.perform(get("/api/submissions/$id").with(auth()))
            .andExpect(jsonPath("$.submission.revision").value(1))
            .andExpect(jsonPath("$.submission.status").value("PENDING"))
    }

    @Test fun `invalid input and unknown achievements return safe errors`() {
        listOf("""{"achievementId":"$achievement","evidence":" "}""",
            """{"achievementId":"${UUID.randomUUID()}","evidence":"Evidence"}""").forEach { body ->
            mvc.perform(post("/api/submissions").with(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
    }

    @Test fun `concurrent review writes have exactly one winner`() {
        val id = UUID.fromString(submit())
        val original = repository.find(id)!!
        val approved = original.submission.approve(UUID.fromString(reviewer), java.time.Instant.now())
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val writes = (1..2).map { pool.submit(Callable {
                start.await()
                try { repository.update(approved, 0); true } catch (_: SubmissionConflict) { false }
            }) }
            start.countDown()
            assertEquals(1, writes.count { it.get(10, java.util.concurrent.TimeUnit.SECONDS) })
            assertEquals(1L, repository.find(id)!!.version)
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM submission_audit", Int::class.java)!!)
        } finally { pool.shutdownNow() }
    }

    @Test fun `audit failure rolls back the review update`() {
        val id = UUID.fromString(submit())
        jdbc.update("INSERT INTO submission_audit (submission_id, version, snapshot) VALUES (?, 1, '{}'::jsonb)", id)
        val original = repository.find(id)!!
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            repository.update(original.submission.approve(UUID.fromString(reviewer), java.time.Instant.now()), 0)
        }
        assertEquals(0L, repository.find(id)!!.version)
    }
}
