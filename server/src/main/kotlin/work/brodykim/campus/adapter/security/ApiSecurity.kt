package work.brodykim.campus.adapter.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter

@Configuration
class ApiSecurity {
    @Bean fun jwtAuthenticationConverter() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter { token ->
            val roles = token.getClaimAsMap("realm_access")?.get("roles") as? List<*> ?: emptyList<Any>()
            if ("reviewer" in roles) listOf(SimpleGrantedAuthority("ROLE_REVIEWER")) else emptyList()
        }
    }

    @Bean fun securityFilterChain(http: HttpSecurity) = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/achievements").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/revocations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/issuers/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/credentials/*/verify").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
        }
        .oauth2ResourceServer { resource ->
            resource.jwt { jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            }
        }.build()
}
