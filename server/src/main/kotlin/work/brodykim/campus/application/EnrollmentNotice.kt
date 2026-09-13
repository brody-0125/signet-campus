package work.brodykim.campus.application

import java.util.UUID

data class EnrollmentNotice(val id: UUID, val recipient: String, val pathwayName: String)
fun interface NotificationSender { fun send(notice: EnrollmentNotice) }
