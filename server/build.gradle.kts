import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.GZIPInputStream
import java.io.DataInputStream
import java.security.MessageDigest
import java.util.Arrays

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jetbrains.kotlinx.kover") version "0.9.3"
}

group = "work.brodykim"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io") {
        content { includeGroup("com.github.brody-0125") }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.github.brody-0125:signet-spring-boot-starter:v0.1.6")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-zipkin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.4")
    testImplementation("io.kotest:kotest-assertions-core:6.0.4")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform { excludeTags("postgres") } }

val postgresTest by tasks.registering(Test::class) {
    description = "Run API and repository integration tests against PostgreSQL"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    shouldRunAfter(tasks.test)
}

kover {
    reports {
        total {
            verify {
                rule { minBound(90) }
            }
        }
    }
}

tasks.check { dependsOn("koverVerify", "verifyDistributionNotices") }

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    from(files("../LICENSE", "../NOTICE", "../THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    from("../docs") {
        include("COMPLIANCE.md", "DEPENDENCY_LICENSES.md", "DISTRIBUTION.md", "third-party/**", "covered-source/**")
        into("META-INF/docs")
    }
    from("../LICENSES/MPL-2.0.txt") { into("META-INF/LICENSES") }
}

tasks.register("verifyDistributionNotices") {
    dependsOn("bootJar")
    doLast {
        val archive = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").get().archiveFile.get().asFile
        ZipFile(archive).use { jar ->
            for (name in listOf("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md")) {
                val entry = checkNotNull(jar.getEntry("META-INF/$name")) { "Missing distribution notice: $name" }
                check(jar.getInputStream(entry).use { it.readBytes() }.contentEquals(file("../$name").readBytes()))
            }
            for (name in listOf("docs/third-party/inventory.json", "docs/DISTRIBUTION.md", "docs/covered-source/README.md", "docs/covered-source/public_suffix_list.dat", "LICENSES/MPL-2.0.txt")) {
                checkNotNull(jar.getEntry("META-INF/$name")) { "Missing distribution evidence: $name" }
            }
            val source = jar.getInputStream(jar.getEntry("META-INF/docs/covered-source/public_suffix_list.dat")).use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) }
            check(digest == "e8b273972eb5a70e888bd3e7d7c5b9b04e600a59d69def9136a74d70ae6fcdd3") { "Public Suffix List source changed" }
            val rules = source.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() && !it.startsWith("//") }.toList()
            val (exceptions, normal) = rules.partition { it.startsWith("!") }
            fun encoded(lines: List<String>): ByteArray = lines
                .sortedWith { left, right -> Arrays.compareUnsigned(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8)) }
                .joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
            val compiled = ZipInputStream(jar.getInputStream(checkNotNull(jar.getEntry("BOOT-INF/lib/okhttp-4.12.0.jar")))).use { nested ->
                check(generateSequence { nested.nextEntry }.any { it.name == "okhttp3/internal/publicsuffix/publicsuffixes.gz" })
                nested.readBytes()
            }
            DataInputStream(GZIPInputStream(compiled.inputStream())).use { input ->
                for (expected in listOf(encoded(normal), encoded(exceptions.map { it.removePrefix("!") }))) {
                    check(input.readInt() == expected.size) { "Public Suffix List rule size differs from runtime artifact" }
                    check(input.readNBytes(expected.size).contentEquals(expected)) { "Covered source does not reproduce runtime rules" }
                }
                check(input.read() == -1)
            }
        }
    }
}
