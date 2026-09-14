package work.brodykim.campus.adapter.web

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import work.brodykim.campus.application.ReviewForbidden
import work.brodykim.campus.application.SubmissionConflict
import work.brodykim.campus.application.SubmissionNotFound
import work.brodykim.campus.application.AchievementNotFound
import work.brodykim.campus.application.AchievementConflict

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(work.brodykim.campus.application.AchievementUnavailable::class)
    fun archived() = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, "Work is paused because an achievement is archived. Contact the issuer about restoration; existing badge records are retained.")
    @ExceptionHandler(work.brodykim.campus.application.PathwayCompletionRequired::class)
    fun incompletePathway() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Earn every completion badge before claiming the pathway award; credentials must be current and not revoked")
    @ExceptionHandler(work.brodykim.campus.application.PrerequisitesRequired::class)
    fun prerequisites() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Earn every prerequisite badge before enrolling; credentials must be current and not revoked")
    @ExceptionHandler(work.brodykim.campus.application.PathwayNotFound::class)
    fun missingPathway() = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Pathway or enrollment not found")
    @ExceptionHandler(AchievementNotFound::class)
    fun missingAchievement() = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Achievement not found")

    @ExceptionHandler(AchievementConflict::class)
    fun lockedAchievement() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Achievement changed or already has submissions; create a new achievement for new criteria")
    @ExceptionHandler(SubmissionNotFound::class)
    fun missing() = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Submission not found")

    @ExceptionHandler(ReviewForbidden::class)
    fun forbidden() = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Reviewer permission required")

    @ExceptionHandler(SubmissionConflict::class, IllegalStateException::class)
    fun conflict() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Submission changed or transition is not permitted")

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class, DataIntegrityViolationException::class)
    fun invalid() = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid submission request")
}
