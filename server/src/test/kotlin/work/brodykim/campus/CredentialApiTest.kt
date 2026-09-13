package work.brodykim.campus

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
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
class CredentialApiTest : SigningTestSupport() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var submissions: SubmissionService
    @Autowired lateinit var credentials: CredentialService
    private val owner = Actor(UUID.fromString("11111111-1111-4111-8111-111111111111"), false)
    private val reviewer = Actor(UUID.fromString("22222222-2222-4222-8222-222222222222"), true)
    private fun auth(actor: Actor = owner, verified: Boolean = true) = jwt()
        .jwt { it.subject(actor.id.toString()).claim("email", "learner@example.test").claim("email_verified", verified) }
        .authorities(if (actor.reviewer) listOf(SimpleGrantedAuthority("ROLE_REVIEWER")) else emptyList())

    @BeforeEach fun reset() {
        check(jdbc.queryForObject("SELECT current_database()", String::class.java) == "campus_test")
        jdbc.execute("TRUNCATE credentials, submission_audit, submissions")
    }
    private fun submission(approved: Boolean = true): UUID {
        val result = submissions.submit(owner, UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), "Private audit notes")
        if (approved) submissions.approve(reviewer, result.submission.id, result.version)
        return result.submission.id
    }
    private fun issue(id: UUID) = mvc.perform(post("/api/submissions/$id/credential").with(auth()))
        .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
        .andReturn().response.contentAsString

    @Test fun `public revocation list exposes only revoked credential identifiers`() {
        val submissionId = submission()
        val signed = issue(submissionId)
        val credential = json.readTree(signed)
        val credentialUrl = credential["id"].asText()
        val origin = credentialUrl.substringBefore("/api/credentials/")
        val listUrl = "$origin/api/revocations"
        assertEquals(json.readTree("""{"id":"$listUrl","type":"1EdTechRevocationList"}"""), credential["credentialStatus"])
        fun list() = mvc.perform(get("/api/revocations").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Cache-Control", "no-store")).andReturn().response.contentAsString
        val empty = """{"id":"$listUrl","issuer":"$origin/api/issuers/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","revokedCredentials":[]}"""
        assertEquals(json.readTree(empty), json.readTree(list()))
        issue(submission()) // Another valid credential must not appear in the public list.
        repeat(2) {
            mvc.perform(post("/api/credentials/${credentialUrl.substringAfterLast('/')}/revoke").with(auth(reviewer)))
                .andExpect(status().isNoContent)
        }
        val expected = json.readTree(empty).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        expected.putArray("revokedCredentials").addObject().put("id", credentialUrl).put("revoked", true)
        assertEquals(expected, json.readTree(list())) // Exact shape excludes names, evidence, email and actor IDs.
        assertEquals(credential, json.readTree(issue(submissionId)))
    }

    @Test fun `approved evidence produces stable credential private download and public verification`() {
        val id = submission()
        val signed = issue(id)
        assertEquals(json.readTree(signed), json.readTree(issue(id)))
        assertFalse(signed.contains("Private audit notes"))
        assertFalse(signed.contains("learner@example.test"))
        val credentialId = json.readTree(signed)["id"].asText().substringAfterLast('/')
        mvc.perform(get("/api/credentials/$credentialId")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/credentials/$credentialId").with(auth(reviewer))).andExpect(status().isNotFound)
        mvc.perform(get("/api/credentials/$credentialId").with(auth())).andExpect(status().isOk)
        mvc.perform(get("/api/submissions/$id/credential").with(auth())).andExpect(status().isOk)
        mvc.perform(post("/api/credentials/$credentialId/verify").contentType(MediaType.APPLICATION_JSON).content(signed))
            .andExpect(status().isOk).andExpect(jsonPath("$.valid").value(true))
        val altered = json.readTree(signed).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        altered.put("unsignedExtra", "This property must not be silently ignored")
        mvc.perform(post("/api/credentials/$credentialId/verify").contentType(MediaType.APPLICATION_JSON).content(altered.toString()))
            .andExpect(jsonPath("$.status").value("ALTERED"))
        mvc.perform(post("/api/credentials/$credentialId/verify").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(jsonPath("$.valid").value(false))
        mvc.perform(get("/api/issuers/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
            .andExpect(status().isOk).andExpect(jsonPath("$.verificationMethod[0].publicKeyMultibase").exists())
            .andExpect(jsonPath("$.d").doesNotExist())
        mvc.perform(post("/api/credentials/$credentialId/revoke").with(auth())).andExpect(status().isForbidden)
        mvc.perform(post("/api/credentials/$credentialId/revoke").with(auth(reviewer))).andExpect(status().isNoContent)
        mvc.perform(post("/api/credentials/$credentialId/verify").contentType(MediaType.APPLICATION_JSON).content(signed))
            .andExpect(jsonPath("$.status").value("REVOKED"))
    }

    @Test fun `issuance requires ownership approval and a verified email`() {
        val pending = submission(false)
        mvc.perform(post("/api/submissions/$pending/credential").with(auth())).andExpect(status().isConflict)
        val approved = submission()
        mvc.perform(post("/api/submissions/$approved/credential").with(auth(reviewer))).andExpect(status().isNotFound)
        mvc.perform(post("/api/submissions/$approved/credential").with(auth(verified = false))).andExpect(status().isBadRequest)
    }

    @Test fun `concurrent issuance converges on one persisted credential`() {
        val id = submission()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(List(2) { Callable { credentials.issue(owner, id, "learner@example.test") } }).map { it.get() }
            assertEquals(results[0].id, results[1].id)
            assertEquals(json.readTree(results[0].document), json.readTree(results[1].document))
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM credentials", Int::class.java))
        } finally { pool.shutdownNow() }
    }
}
