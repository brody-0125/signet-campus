package work.brodykim.campus.application

enum class BadgeImageFormat { PNG, SVG }

interface CredentialImages {
    fun export(document: String, format: BadgeImageFormat): ByteArray
}
