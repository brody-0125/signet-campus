package work.brodykim.campus

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import work.brodykim.campus.adapter.credential.SignetBadgeImages
import work.brodykim.campus.application.BadgeImageFormat
import work.brodykim.signet.baking.BadgeBakingProperties
import work.brodykim.signet.baking.CompositeBadgeBaker
import work.brodykim.signet.baking.PngBadgeBaker
import work.brodykim.signet.baking.SvgBadgeBaker

class CredentialImageTest : StringSpec({
    "portable images retain Unicode and XML delimiters without changing credential text" {
        val properties = BadgeBakingProperties()
        val baker = CompositeBadgeBaker(PngBadgeBaker(properties), SvgBadgeBaker(properties))
        val images = SignetBadgeImages(baker)
        val document = """{"name":"이수 <&> ]]>","type":["VerifiableCredential","OpenBadgeCredential"]}"""
        for (format in BadgeImageFormat.entries) baker.extract(images.export(document, format)) shouldBe document
    }
})
