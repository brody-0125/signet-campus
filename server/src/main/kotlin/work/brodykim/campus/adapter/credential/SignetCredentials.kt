package work.brodykim.campus.adapter.credential

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetKeyPair
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import work.brodykim.campus.application.AchievementSummary
import work.brodykim.campus.application.CredentialCryptography
import work.brodykim.signet.core.BadgeAchievement
import work.brodykim.signet.core.BadgeIssuer
import work.brodykim.signet.credential.CredentialBuilder
import work.brodykim.signet.credential.CredentialSigner
import work.brodykim.signet.credential.KeyPairManager
import java.net.URI
import java.time.Instant
import java.util.UUID

@Component
class SignetCredentials(private val signer: CredentialSigner, private val json: ObjectMapper,
    @Value("\${campus.signing-key}") keyResource: Resource,
    @Value("\${campus.public-url}") base: String,
    @Value("\${campus.verification-keys:classpath:empty-jwks.json}") verificationKeyResource: Resource) : CredentialCryptography {
    private val baseUrl = base.trimEnd('/').also {
        val uri = URI(it)
        require(uri.isAbsolute && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in listOf("localhost", "127.0.0.1")))
    } + "/api"
    private val key = keyResource.inputStream.bufferedReader().use { OctetKeyPair.parse(it.readText()) }.also {
        require(it.isPrivate && it.curve == Curve.Ed25519) { "An Ed25519 private JWK is required" }
        val sample = mapOf<String, Any>("@context" to "https://www.w3.org/ns/credentials/v2", "id" to "urn:uuid:${UUID.randomUUID()}")
        check(signer.verifyDataIntegrity(signer.signWithDataIntegrity(sample, it, "$baseUrl/keys/test"), it.toPublicJWK())) {
            "Signing key public and private components do not match"
        }
    }
    private val issuer = BadgeIssuer(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), "Signet Campus", base.trimEnd('/'), null, "Campus skills recognition")
    private val keyId = "$baseUrl/issuers/${issuer.id()}#${key.computeThumbprint()}"
    private val verificationKeys = (listOf(key.toPublicJWK()) + verificationKeyResource.inputStream.use {
        JWKSet.load(it).keys.map { retained ->
            require(retained is OctetKeyPair && retained.curve == Curve.Ed25519 && !retained.isPrivate) {
                "Verification keys must contain only Ed25519 public JWKs"
            }
            retained
        }
    }).associateBy { "$baseUrl/issuers/${issuer.id()}#${it.computeThumbprint()}" }
    private val mapType = object : TypeReference<Map<String, Any>>() {}

    override fun issue(id: UUID, email: String, achievement: AchievementSummary, at: Instant, until: Instant, pathway: Boolean): String {
        // A separate salt per credential limits cross-credential correlation; it is not a secret.
        val builder = CredentialBuilder(baseUrl, UUID.randomUUID().toString())
        val badge = BadgeAchievement(achievement.id, achievement.name, achievement.criteria, achievement.criteria,
            "Badge", null, listOf("accessibility"))
        val unsigned = builder.buildCredential(id, email, null, badge, issuer, at, until, null, null, null,
            CredentialBuilder.CredentialStatus("$baseUrl/revocations"), false)
        if (pathway) {
            @Suppress("UNCHECKED_CAST")
            val subject = unsigned["credentialSubject"] as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val embedded = subject["achievement"] as Map<String, Any>
            unsigned["credentialSubject"] = subject + ("achievement" to (embedded + ("id" to "$baseUrl/pathways/${achievement.id}")))
        }
        return json.writeValueAsString(signer.signWithDataIntegrity(unsigned, key, keyId))
    }

    override fun verify(document: String): Boolean = try {
        val credential = json.readValue(document, mapType)
        val proof = credential["proof"] as? Map<*, *>
        val verificationKey = verificationKeys[proof?.get("verificationMethod")]
        val embeddedIssuer = credential["issuer"] as? Map<*, *>
        proof?.get("type") == "DataIntegrityProof" && proof["cryptosuite"] == "eddsa-rdfc-2022" &&
            proof["proofPurpose"] == "assertionMethod" && verificationKey != null &&
            embeddedIssuer?.get("id") == "$baseUrl/issuers/${issuer.id()}" &&
            signer.verifyDataIntegrity(credential, verificationKey)
    } catch (_: Exception) { false }

    override fun sameDocument(left: String, right: String): Boolean = try {
        json.readTree(left) == json.readTree(right)
    } catch (_: Exception) { false }

    override fun publicProfile(): Map<String, Any> = mapOf(
        "@context" to listOf("https://www.w3.org/ns/cid/v1", "https://w3id.org/security/multikey/v1"),
        "id" to "$baseUrl/issuers/${issuer.id()}",
        "verificationMethod" to verificationKeys.map { (id, publicKey) -> mapOf("id" to id, "type" to "Multikey",
            "controller" to "$baseUrl/issuers/${issuer.id()}", "publicKeyMultibase" to KeyPairManager.toPublicKeyMultibase(publicKey)) },
        "assertionMethod" to verificationKeys.keys.toList(),
    )
}
