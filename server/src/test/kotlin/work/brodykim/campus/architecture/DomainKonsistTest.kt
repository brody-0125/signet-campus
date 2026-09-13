package work.brodykim.campus.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty

class DomainKonsistTest : StringSpec({
    "domain imports are limited to its own types and the standard library" {
        val domain = Konsist.scopeFromDirectory("src/main/kotlin/work/brodykim/campus/domain")
        domain.files.shouldNotBeEmpty()
        domain.files.assertTrue { file ->
            file.imports.all { imported ->
                listOf("java.", "kotlin.", "work.brodykim.campus.domain.")
                    .any { imported.name.startsWith(it) }
            }
        }
    }
})
