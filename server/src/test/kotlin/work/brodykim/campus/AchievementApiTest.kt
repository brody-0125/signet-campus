package work.brodykim.campus

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import work.brodykim.campus.application.SubmissionService
import work.brodykim.campus.application.Actor

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
class AchievementApiTest : SigningTestSupport() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var transactions: PlatformTransactionManager
    @Autowired lateinit var submissions: SubmissionService
    private fun reviewer() = jwt().jwt { it.subject("22222222-2222-4222-8222-222222222222") }
        .authorities(SimpleGrantedAuthority("ROLE_REVIEWER"))
    private fun create() = json.readTree(mvc.perform(post("/api/achievements").with(reviewer())
        .contentType(MediaType.APPLICATION_JSON).content("""{"name":" Keyboard audit ","criteria":" Check focus order "}"""))
        .andExpect(status().isCreated).andReturn().response.contentAsString)

    @Test fun `reviewer authors catalog entries and stale edits cannot overwrite criteria`() {
        val created = create()
        assertEquals("Keyboard audit", created["name"].asText())
        assertEquals("Check focus order", created["criteria"].asText())
        val path = "/api/achievements/${created["id"].asText()}"
        mvc.perform(get(path)).andExpect(status().isOk).andExpect(jsonPath("$.criteria").value("Check focus order"))
        mvc.perform(get("/api/achievements/${UUID.randomUUID()}")).andExpect(status().isNotFound)
        val change = """{"name":"Keyboard access","criteria":"Check all controls","expectedVersion":0}"""
        mvc.perform(post(path).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(change))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        mvc.perform(post(path).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(change))
            .andExpect(status().isConflict)
        val catalog = json.readTree(mvc.perform(get("/api/achievements")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Check all controls", catalog.first { it["id"] == created["id"] }["criteria"].asText())
    }

    @Test fun `authoring requires reviewer access and nonblank bounded content`() {
        val body = """{"name":"Keyboard audit","criteria":"Check focus order"}"""
        mvc.perform(post("/api/achievements").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/achievements").with(jwt().jwt { it.subject(UUID.randomUUID().toString()) }).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        for (name in listOf(" ", "x".repeat(121))) {
            mvc.perform(post("/api/achievements").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("name" to name, "criteria" to "Audit")))).andExpect(status().isBadRequest)
        }
        mvc.perform(post("/api/achievements/${UUID.randomUUID()}").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Audit","criteria":"Criteria","expectedVersion":0}""")).andExpect(status().isNotFound)
    }

    @Test fun `criteria are frozen once a learner submits evidence`() {
        val created = create()
        mvc.perform(post("/api/submissions").with(jwt().jwt { it.subject(UUID.randomUUID().toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"achievementId":"${created["id"].asText()}","evidence":"Audit notes"}"""))
            .andExpect(status().isCreated)
        mvc.perform(post("/api/achievements/${created["id"].asText()}").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Changed","criteria":"Unassessed new criteria","expectedVersion":0}"""))
            .andExpect(status().isConflict)
    }

    @Test fun `concurrent edit waits for a submission transaction and cannot change its criteria`() {
        val id = UUID.fromString(create()["id"].asText())
        val executor = Executors.newSingleThreadExecutor()
        lateinit var update: Future<Int>
        try {
            TransactionTemplate(transactions).executeWithoutResult {
                submissions.submit(Actor(UUID.randomUUID(), false), id, "Uncommitted evidence")
                update = executor.submit<Int> {
                    mvc.perform(post("/api/achievements/$id").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Changed","criteria":"Different","expectedVersion":0}"""))
                        .andReturn().response.status
                }
                assertThrows(TimeoutException::class.java) { update.get(200, TimeUnit.MILLISECONDS) }
            }
            assertEquals(409, update.get(5, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }
}
