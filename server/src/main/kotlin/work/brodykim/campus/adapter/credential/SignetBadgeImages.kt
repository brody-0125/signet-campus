package work.brodykim.campus.adapter.credential

import org.springframework.stereotype.Component
import work.brodykim.campus.application.BadgeImageFormat
import work.brodykim.campus.application.CredentialImages
import work.brodykim.signet.baking.CompositeBadgeBaker
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
class SignetBadgeImages(private val baker: CompositeBadgeBaker) : CredentialImages {
    override fun export(document: String, format: BadgeImageFormat): ByteArray =
        baker.bake(if (format == BadgeImageFormat.PNG) png() else svg.toByteArray(Charsets.UTF_8), document).imageData()

    private fun png(): ByteArray {
        val image = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(0xB9D5FF)
            graphics.fillPolygon(intArrayOf(150, 230, 196, 150, 96), intArrayOf(290, 314, 464, 418, 434), 5)
            graphics.fillPolygon(intArrayOf(362, 282, 316, 362, 416), intArrayOf(290, 314, 464, 418, 434), 5)
            graphics.color = Color(0x2457E6)
            graphics.fillOval(80, 40, 352, 352)
            graphics.color = Color.WHITE
            graphics.fillPolygon(intArrayOf(256, 282, 354, 299, 316, 256, 196, 213, 158, 230),
                intArrayOf(106, 181, 181, 229, 306, 260, 306, 229, 181, 181), 10)
        } finally { graphics.dispose() }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
    }

    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
        <title>Signet Campus achievement</title>
        <path fill="#b9d5ff" d="M150 290L230 314L196 464L150 418L96 434Z M362 290L282 314L316 464L362 418L416 434Z"/>
        <circle fill="#2457e6" cx="256" cy="216" r="176"/>
        <path fill="#fff" d="M256 106L282 181L354 181L299 229L316 306L256 260L196 306L213 229L158 181L230 181Z"/>
        </svg>""".trimIndent()
}
