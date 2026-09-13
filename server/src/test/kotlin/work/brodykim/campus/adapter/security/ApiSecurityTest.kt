package work.brodykim.campus.adapter.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.oauth2.jwt.Jwt

class ApiSecurityTest : StringSpec({
    "only the realm reviewer role grants review authority" {
        val converter = ApiSecurity().jwtAuthenticationConverter()
        listOf(null, emptyMap<String, Any>(), mapOf("roles" to listOf("learner")), mapOf("roles" to "reviewer"))
            .forEach { realm ->
                val token = Jwt.withTokenValue("test").header("alg", "RS256").subject("learner")
                if (realm != null) token.claim("realm_access", realm)
                converter.convert(token.build())!!.authorities.size shouldBe 0
            }
        val reviewer = Jwt.withTokenValue("test").header("alg", "RS256").subject("reviewer")
            .claim("realm_access", mapOf("roles" to listOf("reviewer"))).build()
        converter.convert(reviewer)!!.authorities.map { it.authority } shouldBe listOf("ROLE_REVIEWER")
    }
})
