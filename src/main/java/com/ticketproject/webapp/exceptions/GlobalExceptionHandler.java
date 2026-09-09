package com.ticketproject.webapp.exceptions;

import com.ticketproject.webapp.dtos.responses.ErrorResponse;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityNotFoundException;

/**
 * GlobalExceptionHandler is a centralized exception handler for all
 * controllers in the application. It catches specific exceptions and
 * returns structured error responses with appropriate HTTP status codes
 * instead of letting the server return a generic 500 Internal Server Error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    /**
     * Handles EventHostEmailAlreadyExistsException by returning a
     * 409 Conflict response.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 409 status
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists
    (
        EmailAlreadyExistsException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles EventHostUnderageException by returning a
     * 400 Bad Request response.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 400 status
     */
    @ExceptionHandler(EventHostUnderageException.class)
    public ResponseEntity<ErrorResponse> handleUnderage
    (
        EventHostUnderageException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles EventHostToVerifyNotFoundException by returning a
     * 404 Not Found response.
     * 
     * @param ex the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 404 status
     */
    @ExceptionHandler(EventHostToVerifyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEventHostToVerifyNotFound
    (
        EventHostToVerifyNotFoundException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles EventHostVerificationPeriodExpiredException by returning a
     * 409 Conflict response.
     * 
     * @param ex the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 409 status
     */
    @ExceptionHandler(EventHostVerificationPeriodExpiredException.class)
    public ResponseEntity<ErrorResponse> handleEventHostVerificationPeriodExpired
    (
        EventHostVerificationPeriodExpiredException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles EventHostInactiveException by returning a
     * 409 Conflict response.
     * 
     * @param ex the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 409 status
     */
    @ExceptionHandler(EventHostInactiveException.class)
    public ResponseEntity<ErrorResponse> handleEventHostInactive
    (
        EventHostInactiveException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles InvalidCredentialsException by returning a
     * 401 Unauthorized response.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 401 status
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials
    (
        InvalidCredentialsException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles AccountNotVerifiedException by returning a
     * 403 Forbidden response.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 403 status
     */
    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotVerified
    (
        AccountNotVerifiedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Handles UnauthorizedException by returning a
     * 401 Unauthorized response.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 401 status
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized
    (
        UnauthorizedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles EmailSendFailedException by returning a
     * 503 Service Unavailable response.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 503 status
     */
    @ExceptionHandler(EmailSendFailedException.class)
    public ResponseEntity<ErrorResponse> handleEmailSendFailed
    (
        EmailSendFailedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Handles MethodArgumentNotValidException (thrown when @Valid
     * validation fails on a request body) by returning a
     * 400 Bad Request response with all field-level validation errors
     * concatenated into the message.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 400 status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors
    (
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    )
    {
        String validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.joining("; "));

        ErrorResponse errorResponse = new ErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            validationErrors,
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles InvalidRequestException by returning a 400 Bad Request response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 400 status
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequests
    (
        InvalidRequestException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles EntityNotFoundException by returning a 404 Not Found response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 404 status
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound
    (
        EntityNotFoundException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles EventAlreadyPublishedException by returning a 409 Conflict response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 409 status
     */
    @ExceptionHandler(EventAlreadyPublishedException.class)
    public ResponseEntity<ErrorResponse> handleEventAlreadyPublished
    (
        EventAlreadyPublishedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles EventAlreadyCanceledException by returning a 409 Conflict response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 409 status
     */
    @ExceptionHandler(EventAlreadyCanceledException.class)
    public ResponseEntity<ErrorResponse> handleEventAlreadyCanceled
    (
        EventAlreadyCanceledException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles EventRegistrationException by returning a 400 Bad Request response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 400 status
     */
    @ExceptionHandler(EventRegistrationException.class)
    public ResponseEntity<ErrorResponse> handleEventRegistration
    (
        EventRegistrationException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles InvalidSignatureException by returning a 400 Bad Request response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 400 status
     */
    @ExceptionHandler(InvalidSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSignature
    (
        InvalidSignatureException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles PendingEventsExistException by returning a 409 Conflict response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 409 status
     */
    @ExceptionHandler(PendingEventsExistException.class)
    public ResponseEntity<ErrorResponse> handlePendingEventsExist
    (
        PendingEventsExistException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles TicketAlreadyScannedException by returning a 409 Conflict response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 409 status
     */
    @ExceptionHandler(TicketAlreadyScannedException.class)
    public ResponseEntity<ErrorResponse> handleTicketAlreadyScanned
    (
        TicketAlreadyScannedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles EventEndedException by returning a 400 Bad Request response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 400 status
     */
    @ExceptionHandler(EventEndedException.class)
    public ResponseEntity<ErrorResponse> handleEventEnded
    (
        EventEndedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles SigningKeyNotFoundException by returning a 500 Internal Server Error response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 500 status
     */
    @ExceptionHandler(SigningKeyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSigningKeyNotFound
    (
        SigningKeyNotFoundException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles TicketScanFailedException by returning a 500 Internal Server Error response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 500 status
     */
    @ExceptionHandler(TicketScanFailedException.class)
    public ResponseEntity<ErrorResponse> handleTicketScanFailed
    (
        TicketScanFailedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles TicketGenerationException by returning a 500 Internal Server Error response.
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 500 status
     */
    @ExceptionHandler(TicketGenerationException.class)
    public ResponseEntity<ErrorResponse> handleTicketGeneration
    (
        TicketGenerationException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles AccessDeniedException by returning a 403 Forbidden response.
     * This is thrown when an operation is attempted with an email address
     * that is not permitted by the configured allowlist.
     *
     * @param ex      the exception that was thrown
     * @param request the HTTP servlet request
     * @return a ResponseEntity containing the error response and a 403 status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied
    (
        AccessDeniedException ex,
        HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse
        (
            Instant.now(),
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.FORBIDDEN);
    }
}
