package work.brodykim.campus

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
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
import work.brodykim.campus.application.*
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
class PathwayApiTest : SigningTestSupport() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var catalog: AchievementCatalogService
    @Autowired lateinit var submissions: SubmissionService
    @Autowired lateinit var credentials: CredentialService
    @Autowired lateinit var pathways: PathwayService
    @Autowired lateinit var completion: PathwayCredentialService
    @Autowired lateinit var credentialRepository: CredentialRepository
    private val learner = Actor(UUID.randomUUID(), false)
    private val reviewer = Actor(UUID.randomUUID(), true)
    private fun auth(actor: Actor = learner) = jwt().jwt { it.subject(actor.id.toString()) }
        .authorities(if (actor.reviewer) listOf(SimpleGrantedAuthority("ROLE_REVIEWER")) else emptyList())
    private fun achievement() = catalog.save(reviewer, null, "Audit", "Review keyboard navigation").id
    private fun body(ids: List<UUID>, prerequisites: List<UUID> = emptyList()) = json.writeValueAsString(mapOf("name" to " Accessible campus ",
        "description" to " Demonstrate accessible content skills ", "achievementIds" to ids, "prerequisiteAchievementIds" to prerequisites))
    private fun create(ids: List<UUID>, prerequisites: List<UUID> = emptyList()) = json.readTree(mvc.perform(post("/api/pathways").with(auth(reviewer))
        .contentType(MediaType.APPLICATION_JSON).content(body(ids, prerequisites))).andExpect(status().isCreated)
        .andReturn().response.contentAsString)
    private fun progress(id: String) = mvc.perform(get("/api/pathways/$id/progress").with(auth()))
        .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
    private fun award(id: UUID): IssuedCredential {
        val submitted = submissions.submit(learner, id, "Audit notes")
        submissions.approve(reviewer, submitted.submission.id, submitted.version)
        return credentials.issue(learner, submitted.submission.id, "learner@example.test")
    }

    @Test fun `reviewer creates a public pathway and enrollment is private and idempotent`() {
        val created = create(listOf(achievement(), achievement()))
        assertEquals("Accessible campus", created["name"].asText())
        val id = created["id"].asText()
        mvc.perform(get("/api/pathways")).andExpect(status().isOk)
        mvc.perform(get("/api/pathways/$id")).andExpect(status().isOk)
            .andExpect(jsonPath("$.achievementIds.length()").value(2))
        mvc.perform(get("/api/pathways/$id/progress")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/pathways/$id/progress").with(auth())).andExpect(status().isNotFound)
        fun enroll() = mvc.perform(post("/api/pathways/$id/enrollment").with(auth())).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store")).andReturn().response.contentAsString
        assertEquals(json.readTree(enroll()), json.readTree(enroll()))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pathway_enrollments WHERE pathway_id = ? AND learner_id = ?",
            Int::class.java, UUID.fromString(id), learner.id)!!)
        progress(id).andExpect(jsonPath("$.completed").value(false)).andExpect(jsonPath("$.earned").value(0))
        mvc.perform(get("/api/pathways/$id/progress").with(auth(reviewer))).andExpect(status().isNotFound)
    }

    @Test fun `authoring rejects unauthorized or invalid requirements without partial persistence`() {
        val id = achievement()
        mvc.perform(post("/api/pathways").contentType(MediaType.APPLICATION_JSON).content(body(listOf(id))))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/api/pathways").with(auth()).contentType(MediaType.APPLICATION_JSON).content(body(listOf(id))))
            .andExpect(status().isForbidden)
        val before = jdbc.queryForObject("SELECT count(*) FROM pathways", Int::class.java)!!
        for (ids in listOf(emptyList(), listOf(id, id), listOf(id, UUID.randomUUID()), List(51) { UUID.randomUUID() })) {
            mvc.perform(post("/api/pathways").with(auth(reviewer)).contentType(MediaType.APPLICATION_JSON).content(body(ids)))
                .andExpect(status().isBadRequest)
        }
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM pathways", Int::class.java)!!)
        val missing = UUID.randomUUID()
        mvc.perform(get("/api/pathways/$missing")).andExpect(status().isNotFound)
        mvc.perform(post("/api/pathways/$missing/enrollment").with(auth())).andExpect(status().isNotFound)
    }

    @Test fun `concurrent enrollment converges and credits an existing award`() {
        val achievement = achievement()
        award(achievement)
        val id = UUID.fromString(create(listOf(achievement))["id"].asText())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(List(2) { Callable { pathways.enroll(learner, id) } }).map { it.get() }
            assertEquals(results[0], results[1])
            assertTrue(results[0].completed)
        } finally { pool.shutdownNow() }
    }

    @Test fun `all prerequisites require current owned awards before first enrollment`() {
        val first = achievement()
        val second = achievement()
        val created = create(listOf(achievement()), listOf(first, second))
        assertEquals(2, created["prerequisiteAchievementIds"].size())
        val id = created["id"].asText()
        fun blocked() {
            mvc.perform(post("/api/pathways/$id/enrollment").with(auth().jwt {
                it.subject(learner.id.toString()).claim("email", "learner@example.test").claim("email_verified", true)
            })).andExpect(status().isConflict)
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pathway_enrollments WHERE pathway_id = ?", Int::class.java, UUID.fromString(id))!!)
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE pathway_id = ?", Int::class.java, UUID.fromString(id))!!)
        }
        blocked()
        val one = award(first)
        blocked()
        val pending = submissions.submit(learner, second, "Prerequisite audit")
        submissions.approve(reviewer, pending.submission.id, pending.version)
        blocked()
        val two = credentials.issue(learner, pending.submission.id, "learner@example.test")
        jdbc.update("UPDATE credentials SET issued_at = now() - interval '2 years', valid_until = now() - interval '1 year' WHERE id = ?", two.id)
        blocked()
        jdbc.update("UPDATE credentials SET issued_at = now() + interval '1 day', valid_until = now() + interval '1 year' WHERE id = ?", two.id)
        blocked()
        jdbc.update("UPDATE credentials SET issued_at = now() - interval '1 hour', valid_until = now() + interval '1 year' WHERE id = ?", two.id)
        mvc.perform(post("/api/pathways/$id/enrollment").with(auth(reviewer))).andExpect(status().isConflict)
        mvc.perform(post("/api/pathways/$id/enrollment").with(auth())).andExpect(status().isOk)
        credentials.revoke(reviewer, one.id)
        mvc.perform(post("/api/pathways/$id/enrollment").with(auth())).andExpect(status().isOk)
        progress(id).andExpect(jsonPath("$.total").value(1))
        val newPath = create(listOf(achievement()), listOf(first))["id"].asText()
        mvc.perform(post("/api/pathways/$newPath/enrollment").with(auth())).andExpect(status().isConflict)
    }

    @Test fun `invalid prerequisite sets roll back publication`() {
        val required = achievement()
        val prerequisite = achievement()
        val before = jdbc.queryForObject("SELECT count(*) FROM pathways", Int::class.java)!!
        for (ids in listOf(listOf(required), listOf(prerequisite, prerequisite), listOf(UUID.randomUUID()), List(51) { UUID.randomUUID() })) {
            mvc.perform(post("/api/pathways").with(auth(reviewer)).contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(required), ids))).andExpect(status().isBadRequest)
        }
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM pathways", Int::class.java)!!)
    }

    @Test fun `progress counts distinct current awards and regresses after expiry or revocation`() {
        val first = achievement()
        val second = achievement()
        val id = create(listOf(first, second))["id"].asText()
        mvc.perform(post("/api/pathways/$id/enrollment").with(auth())).andExpect(status().isOk)
        val pending = submissions.submit(learner, first, "Pending audit")
        progress(id).andExpect(jsonPath("$.earned").value(0))
        submissions.approve(reviewer, pending.submission.id, pending.version)
        progress(id).andExpect(jsonPath("$.earned").value(0))
        val one = credentials.issue(learner, pending.submission.id, "learner@example.test")
        val duplicate = award(first)
        progress(id).andExpect(jsonPath("$.earned").value(1)).andExpect(jsonPath("$.total").value(2))
        val two = award(second)
        progress(id).andExpect(jsonPath("$.completed").value(true))
        credentials.revoke(reviewer, one.id)
        progress(id).andExpect(jsonPath("$.completed").value(true))
        credentials.revoke(reviewer, duplicate.id)
        progress(id).andExpect(jsonPath("$.earned").value(1)).andExpect(jsonPath("$.completed").value(false))
        jdbc.update("UPDATE credentials SET issued_at = now() - interval '2 years', valid_until = now() - interval '1 year' WHERE id = ?", two.id)
        progress(id).andExpect(jsonPath("$.earned").value(0))
        jdbc.update("UPDATE credentials SET issued_at = now() + interval '1 day', valid_until = now() + interval '1 year' WHERE id = ?", two.id)
        progress(id).andExpect(jsonPath("$.earned").value(0))
    }

    @Test fun `completion award requires enrollment and all current credentials`() {
        val required = achievement()
        val id = create(listOf(required))["id"].asText()
        fun verified() = auth().jwt {
            it.subject(learner.id.toString()).claim("email", "learner@example.test").claim("email_verified", true)
        }
        mvc.perform(post("/api/pathways/$id/credential")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/pathways/$id/credential").with(verified())).andExpect(status().isNotFound)
        mvc.perform(post("/api/pathways/$id/enrollment").with(auth())).andExpect(status().isOk)
        mvc.perform(post("/api/pathways/$id/credential").with(verified())).andExpect(status().isConflict)
        val component = award(required)
        credentials.revoke(reviewer, component.id)
        mvc.perform(post("/api/pathways/$id/credential").with(verified())).andExpect(status().isConflict)
        mvc.perform(get("/api/pathways/$id/credential").with(auth())).andExpect(status().isNotFound)
    }

    @Test fun `completion award is private repeatable and independently revocable`() {
        val required = achievement()
        val component = award(required)
        val id = create(listOf(required))["id"].asText()
        pathways.enroll(learner, UUID.fromString(id))
        fun issue() = mvc.perform(post("/api/pathways/$id/credential").with(auth().jwt {
            it.subject(learner.id.toString()).claim("email", "learner@example.test").claim("email_verified", true)
        })).andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().contentType("application/vc+ld+json")).andReturn().response.contentAsString
        mvc.perform(post("/api/pathways/$id/credential").with(auth())).andExpect(status().isBadRequest)
        val document = issue()
        val credentialId = json.readTree(document)["id"].asText().substringAfterLast('/')
        assertNotEquals(component.id.toString(), credentialId)
        assertEquals(json.readTree(document), json.readTree(issue()))
        mvc.perform(get("/api/pathways/$id/credential").with(auth())).andExpect(status().isOk)
            .andExpect(content().json(document))
        mvc.perform(get("/api/pathways/$id/credential").with(auth(reviewer))).andExpect(status().isNotFound)
        mvc.perform(get("/api/credentials/$credentialId").with(auth(reviewer))).andExpect(status().isNotFound)
        fun verify(expected: String) = mvc.perform(post("/api/credentials/$credentialId/verify")
            .contentType(MediaType.APPLICATION_JSON).content(document)).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(expected))
        verify("VALID")
        credentials.revoke(reviewer, component.id)
        progress(id).andExpect(jsonPath("$.completed").value(false))
        assertEquals(json.readTree(document), json.readTree(issue()))
        verify("VALID")
        mvc.perform(post("/api/credentials/$credentialId/revoke").with(auth())).andExpect(status().isForbidden)
        mvc.perform(post("/api/credentials/$credentialId/revoke").with(auth(reviewer))).andExpect(status().isNoContent)
        verify("REVOKED")
        assertEquals(json.readTree(document), json.readTree(issue()))
    }

    @Test fun `concurrent completion converges without fabricated submissions and SQL rechecks eligibility`() {
        val required = achievement()
        val component = award(required)
        val id = UUID.fromString(create(listOf(required))["id"].asText())
        pathways.enroll(learner, id)
        val before = jdbc.queryForObject("SELECT count(*) FROM submissions WHERE learner_id = ?", Int::class.java, learner.id)
        val pool = Executors.newFixedThreadPool(2)
        val record = try {
            val records = pool.invokeAll(List(2) { Callable { completion.issue(learner, id, "learner@example.test") } }).map { it.get() }
            assertEquals(records[0], records[1])
            records.first()
        } finally { pool.shutdownNow() }
        assertNull(record.submissionId)
        assertEquals(id, record.pathwayId)
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM submissions WHERE learner_id = ?", Int::class.java, learner.id))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM credentials WHERE pathway_id = ? AND learner_id = ?", Int::class.java, id, learner.id))
        val embeddedId = json.readTree(record.document)["credentialSubject"]["achievement"]["id"].asText()
        assertTrue(embeddedId.endsWith("/api/pathways/$id"))
        mvc.perform(get("/api/pathways/$id")).andExpect(status().isOk)

        val otherPath = UUID.fromString(create(listOf(required))["id"].asText())
        pathways.enroll(learner, otherPath)
        pathways.enroll(reviewer, otherPath)
        assertThrows(PathwayCompletionRequired::class.java) { completion.issue(reviewer, otherPath, "reviewer@example.test") }
        for (dates in listOf("issued_at = now() - interval '2 years', valid_until = now() - interval '1 year'",
            "issued_at = now() + interval '1 day', valid_until = now() + interval '1 year'")) {
            jdbc.update("UPDATE credentials SET $dates WHERE id = ?", component.id)
            assertThrows(PathwayCompletionRequired::class.java) { completion.issue(learner, otherPath, "learner@example.test") }
            assertThrows(PathwayCompletionRequired::class.java) {
                credentialRepository.savePathwayIfAbsent(record.copy(id = UUID.randomUUID(), pathwayId = otherPath))
            }
        }
        assertNull(credentialRepository.findByPathway(otherPath, learner.id))
    }
}
