package work.brodykim.campus

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import work.brodykim.signet.credential.KeyPairManager
import java.nio.file.Files

abstract class SigningTestSupport {
    companion object {
        private val signingKey = Files.createTempFile("campus-test-", ".jwk").also {
            Files.writeString(it, KeyPairManager.generateEd25519KeyPair().toJSONString())
            it.toFile().deleteOnExit()
        }
        @JvmStatic @DynamicPropertySource
        fun signingConfiguration(properties: DynamicPropertyRegistry) {
            properties.add("campus.signing-key") { signingKey.toUri().toString() }
        }
    }
}
