package work.brodykim.campus

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Tag("postgres")
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "management.server.port=0", "management.endpoints.web.exposure.include=health,prometheus",
    "management.tracing.enabled=true", "management.tracing.sampling.probability=0"
])
class TelemetryApiTest : SigningTestSupport() {
    @TestConfiguration
    class LogProbe {
        @Bean fun correlationProbe() = object : OncePerRequestFilter() {
            override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
                org.slf4j.LoggerFactory.getLogger("telemetry.probe").info("Correlation probe {}", org.slf4j.MDC.get("traceId"))
                chain.doFilter(request, response)
            }
        }
    }
    @LocalServerPort private var port = 0
    @LocalManagementPort private var managementPort = 0
    private fun get(target: Int, path: String) = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://localhost:$target$path"))
            .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-00").GET().build(), HttpResponse.BodyHandlers.ofString())

    @Test fun `metrics are collected privately without raw request values`(output: CapturedOutput) {
        assertNotEquals(port, managementPort)
        val secret = "sensitive-evidence-canary"
        assertEquals(200, get(port, "/api/achievements?evidence=$secret").statusCode())
        assertTrue(output.all.lineSequence().any { it.contains("Correlation probe") && it.contains("0123456789abcdef0123456789abcdef") })
        assertEquals(401, get(port, "/api/submissions").statusCode())
        for (target in listOf(port, managementPort)) {
            assertTrue(get(target, "/actuator/env").statusCode() in listOf(401, 403, 404))
        }
        assertTrue(get(port, "/actuator/prometheus").statusCode() in listOf(401, 403, 404))
        val metrics = get(managementPort, "/actuator/prometheus")
        assertEquals(200, metrics.statusCode())
        assertTrue(metrics.body().contains("http_server_requests_seconds_count"))
        assertTrue(metrics.body().contains("uri=\"/api/achievements\""))
        assertTrue(metrics.body().contains("jvm_memory_used_bytes"))
        assertFalse(metrics.body().contains(secret))
    }
}
