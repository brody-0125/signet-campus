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
import work.brodykim.campus.application.*
import java.util.UUID
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
class SuccessorApiTest : SigningTestSupport() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var catalog: AchievementCatalogService
    @Autowired lateinit var submissions: SubmissionService
    @Autowired lateinit var credentials: CredentialService
    @Autowired lateinit var pathways: PathwayService
    @Autowired lateinit var transactions: PlatformTransactionManager
    private val reviewer = Actor(UUID.randomUUID(), true)
    private val owner = Actor(UUID.randomUUID(), false)
    private fun post(path: String, body: Any, actor: Actor = reviewer) = mvc.perform(post("/api$path")
        .with(jwt().jwt { it.subject(actor.id.toString()) }.authorities(if (actor.reviewer) listOf(SimpleGrantedAuthority("ROLE_REVIEWER")) else emptyList()))
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))

    @Test fun `a successor stays private until publication and never rewrites prior awards or pathways`() {
        val source = catalog.save(reviewer, null, "Original audit", "Original assessment").let { catalog.publish(reviewer, it.id, it.version) }
        val submission = submissions.submit(owner, source.id, "Evidence")
        submissions.approve(reviewer, submission.submission.id, submission.version)
        val original = credentials.issue(owner, submission.submission.id, "learner@example.test")
        val path = pathways.create(reviewer, "Original pathway", "Original requirements", listOf(source.id))
        pathways.enroll(owner, path.id)
        val newcomer = Actor(UUID.randomUUID(), false)
        pathways.enroll(newcomer, path.id)
        val archived = catalog.archive(reviewer, source.id, source.version, true)
        val body = mapOf("name" to "Next audit", "criteria" to "Updated assessment", "expectedVersion" to archived.version)
        val response = post("/achievements/${source.id}/successors", body).andExpect(status().isCreated)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.predecessorId").value(source.id.toString()))
            .andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.archived").value(false))
            .andExpect(jsonPath("$.version").value(0)).andReturn().response
        val id = UUID.fromString(json.readTree(response.contentAsString)["id"].asText())
        assertNotEquals(source.id, id)
        mvc.perform(get("/api/achievements/$id")).andExpect(status().isNotFound)
        val publicList = json.readTree(mvc.perform(get("/api/achievements?includeArchived=true")).andReturn().response.contentAsString)
        assertFalse(publicList.any { it["id"].asText() == id.toString() })
        post("/achievements/$id", mapOf("name" to "Next edition", "criteria" to "New criteria", "expectedVersion" to 0,
            "predecessorId" to UUID.randomUUID())).andExpect(status().isOk)
            .andExpect(jsonPath("$.predecessorId").value(source.id.toString()))
        post("/achievements/$id/publish", mapOf("expectedVersion" to 1)).andExpect(status().isOk)
            .andExpect(jsonPath("$.predecessorId").value(source.id.toString()))
        mvc.perform(get("/api/achievements/$id")).andExpect(status().isOk)
            .andExpect(jsonPath("$.predecessorId").value(source.id.toString()))
        assertEquals(archived, catalog.list(reviewer).first { it.id == source.id })
        assertEquals(original.document, credentials.issue(owner, submission.submission.id, "learner@example.test").document)
        assertEquals("VALID", credentials.verify(original.id, original.document).status)
        assertEquals(listOf(source.id), pathways.list().first { it.id == path.id }.achievementIds)
        val newWork = submissions.submit(newcomer, id, "New edition evidence")
        submissions.approve(reviewer, newWork.submission.id, newWork.version)
        credentials.issue(newcomer, newWork.submission.id, "newcomer@example.test")
        assertFalse(pathways.progress(newcomer, path.id).completed)
        assertTrue(pathways.progress(owner, path.id).completed)
    }

    @Test fun `successor creation requires reviewer and a current published source`() {
        val draft = catalog.save(reviewer, null, "Private source", "Criteria")
        fun body(version: Long) = mapOf("name" to "Next", "criteria" to "Criteria", "expectedVersion" to version)
        val endpoint = "/achievements/${draft.id}/successors"
        mvc.perform(post("/api$endpoint").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(0))))
            .andExpect(status().isUnauthorized)
        post(endpoint, body(0), owner).andExpect(status().isForbidden)
        post(endpoint, body(0)).andExpect(status().isConflict)
        val source = catalog.publish(reviewer, draft.id, 0)
        post(endpoint, body(0)).andExpect(status().isConflict)
        post(endpoint, body(-1)).andExpect(status().isBadRequest)
        post(endpoint, body(source.version) + ("name" to " ")).andExpect(status().isBadRequest)
        post("/achievements/${UUID.randomUUID()}/successors", body(0)).andExpect(status().isNotFound)
        post(endpoint, body(source.version)).andExpect(status().isCreated)
        post(endpoint, body(source.version)).andExpect(status().isCreated)
    }

    @Test fun `concurrent source changes invalidate successor creation after the lock is released`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            for (archive in listOf(false, true)) {
                val source = catalog.save(reviewer, null, "Concurrent edition", "Source criteria").let { catalog.publish(reviewer, it.id, it.version) }
                lateinit var result: java.util.concurrent.Future<Int>
                TransactionTemplate(transactions).executeWithoutResult {
                    if (archive) catalog.archive(reviewer, source.id, source.version, true)
                    else catalog.save(reviewer, source.id, "Edited source", "Edited criteria", source.version)
                    result = executor.submit<Int> { post("/achievements/${source.id}/successors", mapOf(
                        "name" to "Next", "criteria" to "Criteria", "expectedVersion" to source.version)).andReturn().response.status }
                    assertThrows(TimeoutException::class.java) { result.get(200, TimeUnit.MILLISECONDS) }
                }
                assertEquals(409, result.get(10, TimeUnit.SECONDS))
                assertFalse(catalog.list(reviewer).any { it.predecessorId == source.id })
            }
        } finally { executor.shutdownNow() }
    }
}
