package com.skrymer.udgaard.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.time.format.DateTimeParseException

data class ErrorResponse(
  val message: String,
  val timestamp: Instant = Instant.now(),
  val fieldErrors: Map<String, String>? = null,
)

@RestControllerAdvice
class GlobalExceptionHandler {
  @ExceptionHandler(IllegalArgumentException::class)
  fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
    ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "Bad request"))

  @ExceptionHandler(DateTimeParseException::class)
  fun handleDateTimeParse(ex: DateTimeParseException): ResponseEntity<ErrorResponse> =
    ResponseEntity.badRequest().body(ErrorResponse("Invalid date format: ${ex.parsedString}"))

  @ExceptionHandler(NoSuchElementException::class)
  fun handleNoSuchElement(ex: NoSuchElementException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "Not found"))

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
    val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid") }
    return ResponseEntity.badRequest().body(
      ErrorResponse(
        message = "Validation failed",
        fieldErrors = fieldErrors,
      ),
    )
  }

  @ExceptionHandler(Exception::class)
  fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
    logger.error("Unhandled exception: ${ex.message}", ex)
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ErrorResponse("Internal server error"))
  }

  companion object {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
  }
}
