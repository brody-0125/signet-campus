package work.brodykim.campus

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import work.brodykim.campus.application.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
class ArchivalApiTest : SigningTestSupport() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var catalog: AchievementCatalogService
    @Autowired lateinit var submissions: SubmissionService
    @Autowired lateinit var credentials: CredentialService
    @Autowired lateinit var pathways: PathwayService
    @Autowired lateinit var completion: PathwayCredentialService
    @Autowired lateinit var transactions: PlatformTransactionManager
    private val owner = Actor(UUID.randomUUID(), false)
    private val reviewer = Actor(UUID.randomUUID(), true)
    private fun auth(actor: Actor) = jwt().jwt { it.subject(actor.id.toString()).claim("email", "learner@example.test").claim("email_verified", true) }
        .authorities(if (actor.reviewer) listOf(SimpleGrantedAuthority("ROLE_REVIEWER")) else emptyList())
    private fun achievement() = catalog.save(reviewer, null, "Archive audit", "Assess keyboard access")
        .let { catalog.publish(reviewer, it.id, it.version) }
    private fun post(path: String, actor: Actor, body: Any = emptyMap<String, Any>()) = mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api$path").with(auth(actor))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
    private fun archive(id: UUID, version: Long, archived: Boolean = true, actor: Actor = reviewer) =
        post("/achievements/$id/archive", actor, mapOf("expectedVersion" to version, "archived" to archived))

    @Test fun `archival pauses new work while preserving earned badges and restoration resumes work`() {
        val a = achievement()
        val earned = submissions.submit(owner, a.id, "Completed audit")
        submissions.approve(reviewer, earned.submission.id, earned.version)
        val original = credentials.issue(owner, earned.submission.id, "learner@example.test")
        val pending = submissions.submit(owner, a.id, "Awaiting assessment")
        val rejected = submissions.submit(owner, a.id, "Needs corrections")
        val feedback = submissions.reject(reviewer, rejected.submission.id, rejected.version, "Add evidence")
        val path = pathways.create(reviewer, "Archive pathway", "Keep existing progress", listOf(a.id))
        val prerequisitePath = pathways.create(reviewer, "Archive prerequisite", "Retain prerequisite", listOf(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")), listOf(a.id))
        pathways.enroll(owner, path.id)
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/achievements/${a.id}/archive")
            .contentType(MediaType.APPLICATION_JSON).content("""{"expectedVersion":1,"archived":true}""")).andExpect(status().isUnauthorized)
        archive(a.id, a.version, actor = owner).andExpect(status().isForbidden)
        post("/achievements/${a.id}/archive", reviewer, mapOf("expectedVersion" to a.version)).andExpect(status().isBadRequest)
        archive(a.id, -1).andExpect(status().isBadRequest)
        archive(a.id, a.version + 1).andExpect(status().isConflict)
        archive(UUID.randomUUID(), 0).andExpect(status().isNotFound)
        val draft = catalog.save(reviewer, null, "Draft", "Unpublished")
        archive(draft.id, 0).andExpect(status().isConflict)
        archive(a.id, a.version).andExpect(status().isOk).andExpect(jsonPath("$.archived").value(true))
        post("/achievements/${a.id}", reviewer, mapOf("name" to "Changed", "criteria" to "Changed", "expectedVersion" to a.version + 1)).andExpect(status().isConflict)
        post("/pathways", reviewer, mapOf("name" to "New pathway", "description" to "Archived requirement", "achievementIds" to listOf(a.id)))
            .andExpect(status().isLocked)
        archive(a.id, a.version + 1).andExpect(status().isConflict)
        mvc.perform(get("/api/achievements/${a.id}")).andExpect(status().isOk).andExpect(jsonPath("$.archived").value(true))
        val visible = json.readTree(mvc.perform(get("/api/achievements")).andReturn().response.contentAsString)
        assertFalse(visible.any { it["id"].asText() == a.id.toString() })
        val history = json.readTree(mvc.perform(get("/api/achievements?includeArchived=true")).andReturn().response.contentAsString)
        assertTrue(history.any { it["id"].asText() == a.id.toString() })
        assertFalse(history.any { it["id"].asText() == draft.id.toString() })
        assertEquals(original.document, credentials.issue(owner, earned.submission.id, "learner@example.test").document)
        assertEquals("VALID", credentials.verify(original.id, original.document).status)
        post("/submissions", owner, mapOf("achievementId" to a.id, "evidence" to "New work")).andExpect(status().isLocked)
        post("/submissions/${rejected.submission.id}/resubmit", owner, mapOf("expectedVersion" to feedback.version, "evidence" to "Updated work"))
            .andExpect(status().isLocked)
        submissions.approve(reviewer, pending.submission.id, pending.version)
        post("/submissions/${pending.submission.id}/credential", owner).andExpect(status().isLocked)
        mvc.perform(get("/api/pathways/${path.id}")).andExpect(status().isOk).andExpect(jsonPath("$.paused").value(true))
        mvc.perform(get("/api/pathways/${prerequisitePath.id}")).andExpect(status().isOk).andExpect(jsonPath("$.paused").value(true))
        post("/pathways/${prerequisitePath.id}/enrollment", Actor(UUID.randomUUID(), false)).andExpect(status().isLocked)
        post("/pathways/${path.id}/enrollment", Actor(UUID.randomUUID(), false)).andExpect(status().isLocked)
        post("/pathways/${path.id}/enrollment", owner).andExpect(status().isOk)
        assertTrue(pathways.progress(owner, path.id).completed)
        assertEquals("VALID", credentials.verify(completion.issue(owner, path.id, "learner@example.test").id,
            completion.get(owner, path.id).document).status)
        archive(a.id, a.version + 1, false).andExpect(status().isOk).andExpect(jsonPath("$.archived").value(false))
        post("/submissions/${pending.submission.id}/credential", owner).andExpect(status().isOk)
        post("/submissions/${rejected.submission.id}/resubmit", owner, mapOf("expectedVersion" to feedback.version, "evidence" to "Updated work"))
            .andExpect(status().isOk)
        post("/pathways/${path.id}/enrollment", Actor(UUID.randomUUID(), false)).andExpect(status().isOk)
        assertEquals(original.document, credentials.get(owner, original.id).document)
    }

    @Test fun `in flight writes wait for archive transaction and cannot bypass the pause`() {
        val a = achievement()
        val approved = submissions.submit(owner, a.id, "Approved work")
        submissions.approve(reviewer, approved.submission.id, approved.version)
        val rejected = submissions.submit(owner, a.id, "Rejected work")
        val feedback = submissions.reject(reviewer, rejected.submission.id, rejected.version, "More evidence")
        val path = pathways.create(reviewer, "Concurrent archive", "Pause atomically", listOf(a.id))
        val executor = Executors.newFixedThreadPool(4)
        val writes = mutableListOf<Future<Int>>()
        try {
            TransactionTemplate(transactions).executeWithoutResult {
                archive(a.id, a.version).andExpect(status().isOk)
                val requests = listOf(
                    { post("/submissions", owner, mapOf("achievementId" to a.id, "evidence" to "Racing work")) },
                    { post("/submissions/${approved.submission.id}/credential", owner) },
                    { post("/submissions/${rejected.submission.id}/resubmit", owner, mapOf("expectedVersion" to feedback.version, "evidence" to "Racing update")) },
                    { post("/pathways/${path.id}/enrollment", owner) },
                )
                requests.forEach { request -> writes += executor.submit<Int> { request().andReturn().response.status } }
                writes.forEach { assertThrows(TimeoutException::class.java) { it.get(200, TimeUnit.MILLISECONDS) } }
            }
            writes.forEach { assertEquals(423, it.get(10, TimeUnit.SECONDS)) }
            assertThrows(SubmissionNotFound::class.java) { credentials.forSubmission(owner, approved.submission.id) }
            assertEquals(feedback.version, submissions.get(owner, rejected.submission.id).version)
            assertThrows(PathwayNotFound::class.java) { pathways.progress(owner, path.id) }
        } finally { executor.shutdownNow() }
    }
}
