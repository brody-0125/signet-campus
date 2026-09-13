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

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(SubmissionNotFound::class)
    fun missing() = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Submission not found")

    @ExceptionHandler(ReviewForbidden::class)
    fun forbidden() = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Reviewer permission required")

    @ExceptionHandler(SubmissionConflict::class, IllegalStateException::class)
    fun conflict() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Submission changed or transition is not permitted")

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class, DataIntegrityViolationException::class)
    fun invalid() = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid submission request")
}
