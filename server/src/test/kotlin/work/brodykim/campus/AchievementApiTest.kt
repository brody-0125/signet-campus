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
        mvc.perform(get("/api/reviewer/achievements/${created["id"].asText()}").with(reviewer()))
            .andExpect(status().isOk).andExpect(jsonPath("$.criteria").value("Check focus order"))
        mvc.perform(get("/api/achievements/${UUID.randomUUID()}")).andExpect(status().isNotFound)
        val change = """{"name":"Keyboard access","criteria":"Check all controls","expectedVersion":0}"""
        mvc.perform(post(path).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(change))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        mvc.perform(post(path).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(change))
            .andExpect(status().isConflict)
        val catalog = json.readTree(mvc.perform(get("/api/reviewer/achievements").with(reviewer())).andExpect(status().isOk).andReturn().response.contentAsString)
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
        mvc.perform(post("/api/achievements/${created["id"].asText()}/publish").with(reviewer())
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":0}""")).andExpect(status().isOk)
        mvc.perform(post("/api/submissions").with(jwt().jwt { it.subject(UUID.randomUUID().toString()) })
            .contentType(MediaType.APPLICATION_JSON).content("""{"achievementId":"${created["id"].asText()}","evidence":"Audit notes"}"""))
            .andExpect(status().isCreated)
        mvc.perform(post("/api/achievements/${created["id"].asText()}").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Changed","criteria":"Unassessed new criteria","expectedVersion":1}"""))
            .andExpect(status().isConflict)
    }

    @Test fun `concurrent edit waits for a submission transaction and cannot change its criteria`() {
        val id = UUID.fromString(create()["id"].asText())
        mvc.perform(post("/api/achievements/$id/publish").with(reviewer())
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":0}""")).andExpect(status().isOk)
        val executor = Executors.newSingleThreadExecutor()
        lateinit var update: Future<Int>
        try {
            TransactionTemplate(transactions).executeWithoutResult {
                submissions.submit(Actor(UUID.randomUUID(), false), id, "Uncommitted evidence")
                update = executor.submit<Int> {
                    mvc.perform(post("/api/achievements/$id").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Changed","criteria":"Different","expectedVersion":1}"""))
                        .andReturn().response.status
                }
                assertThrows(TimeoutException::class.java) { update.get(200, TimeUnit.MILLISECONDS) }
            }
            assertEquals(409, update.get(5, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }

    @Test fun `drafts stay private and unusable until an authorized versioned publication`() {
        val draft = create()
        val id = draft["id"].asText()
        assertFalse(draft["published"].asBoolean())
        val learner = jwt().jwt { it.subject(UUID.randomUUID().toString()) }
        mvc.perform(get("/api/achievements/$id")).andExpect(status().isNotFound)
        val public = json.readTree(mvc.perform(get("/api/achievements")).andReturn().response.contentAsString)
        assertFalse(public.any { it["id"].asText() == id })
        for (path in listOf("/api/reviewer/achievements", "/api/reviewer/achievements/$id")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized)
            mvc.perform(get(path).with(learner)).andExpect(status().isForbidden)
            mvc.perform(get(path).with(reviewer())).andExpect(status().isOk)
                .andExpect(header().string("Cache-Control", "no-store"))
        }
        val evidence = """{"achievementId":"$id","evidence":"Audit notes"}"""
        mvc.perform(post("/api/submissions").with(learner).contentType(MediaType.APPLICATION_JSON).content(evidence))
            .andExpect(status().isBadRequest)
        for (field in listOf("achievementIds", "prerequisiteAchievementIds")) {
            val requirements = if (field == "achievementIds") listOf(id) else listOf("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            val prerequisites = if (field == "prerequisiteAchievementIds") listOf(id) else emptyList()
            mvc.perform(post("/api/pathways").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("name" to "Draft pathway", "description" to "Audit", "achievementIds" to requirements,
                    "prerequisiteAchievementIds" to prerequisites)))).andExpect(status().isBadRequest)
        }
        val publish = "/api/achievements/$id/publish"
        val version = """{"expectedVersion":0}"""
        mvc.perform(post(publish).contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isUnauthorized)
        mvc.perform(post(publish).with(learner).contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isForbidden)
        mvc.perform(post(publish).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":-1}"""))
            .andExpect(status().isBadRequest)
        mvc.perform(post(publish).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":1}"""))
            .andExpect(status().isConflict)
        mvc.perform(post(publish).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(version))
            .andExpect(status().isOk).andExpect(jsonPath("$.published").value(true)).andExpect(jsonPath("$.version").value(1))
        mvc.perform(post(publish).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isConflict)
        mvc.perform(post(publish).with(reviewer()).contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":1}"""))
            .andExpect(status().isConflict)
        mvc.perform(post("/api/achievements/${UUID.randomUUID()}/publish").with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(version))
            .andExpect(status().isNotFound)
        mvc.perform(get("/api/achievements/$id")).andExpect(status().isOk).andExpect(jsonPath("$.published").value(true))
        mvc.perform(post("/api/submissions").with(learner).contentType(MediaType.APPLICATION_JSON).content(evidence)).andExpect(status().isCreated)
    }

    @Test fun `publication locks its saved version and concurrent publishers cannot both succeed`() {
        val id = create()["id"].asText()
        val executor = Executors.newSingleThreadExecutor()
        lateinit var publish: Future<Int>
        try {
            TransactionTemplate(transactions).executeWithoutResult {
                mvc.perform(post("/api/achievements/$id/publish").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expectedVersion":0}""")).andExpect(status().isOk)
                publish = executor.submit<Int> {
                    mvc.perform(post("/api/achievements/$id/publish").with(reviewer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""{"expectedVersion":0}""")).andReturn().response.status
                }
                assertThrows(TimeoutException::class.java) { publish.get(200, TimeUnit.MILLISECONDS) }
            }
            assertEquals(409, publish.get(5, TimeUnit.SECONDS))
            mvc.perform(get("/api/achievements/$id")).andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        } finally { executor.shutdownNow() }
    }
}
