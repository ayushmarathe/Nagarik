package com.nagarik.web;

import com.nagarik.error.ConflictException;
import com.nagarik.error.ForbiddenException;
import com.nagarik.error.NotFoundException;
import com.nagarik.error.UnauthenticatedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into a consistent shape: always a "message" the interface can
 * show directly, plus per-field messages when a form was the problem.
 *
 * Every failure the API can produce needs a handler here. Anything that falls
 * through gets Spring's default error body, which has no "message" field, so
 * the interface silently degrades to "Something went wrong. Try again."
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidationError(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", fields.isEmpty() ? "Check the form and try again" : fields.values().iterator().next());
        body.put("fields", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(exception.getMessage()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> onUnauthenticated(UnauthenticatedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message(exception.getMessage()));
    }

    /**
     * A sign-in attempt that failed, or a request that arrived without a usable
     * token. Same 401 either way, and the same wording the security filter uses
     * so the interface has one string to recognise.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> onAuthenticationFailure(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sign in to do that"));
    }

    /**
     * Signed in, wrong role. This is the one Spring Security would otherwise
     * answer on its own through the security filter; handling it here as well
     * keeps the body shape identical to every other error.
     */
    @ExceptionHandler({ ForbiddenException.class, AccessDeniedException.class })
    public ResponseEntity<Map<String, Object>> onForbidden(Exception exception) {
        String text = exception instanceof ForbiddenException
                ? exception.getMessage()
                : "Only a moderator can change a report's status";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message(text));
    }

    /**
     * A well-formed value that clashed with something already stored - an email
     * or display name that is taken. The service checks for these first so it
     * can name the offending field; this is the backstop for the race where two
     * people register the same address at the same instant.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> onExplicitConflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message(exception.getMessage()));
    }

    /**
     * An unknown enum in the query string ("?category=potholes"), or a body that
     * is not valid JSON. The exception message names internal class paths, so it
     * is deliberately not passed through to the person.
     */
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<Map<String, Object>> onUnreadableRequest(Exception exception) {
        String text = "The server could not read that request. Check the values and try again.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message(text));
    }

    /**
     * The unique constraints, catching what the service-level checks raced past:
     * a double-clicked vote hitting (issue_id, user_id), or two registrations
     * claiming the same name at the same moment. That is a clash, not a fault,
     * so it should not surface as a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> onConflict(DataIntegrityViolationException exception) {
        String text = "That clashed with something already saved. Try again.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message(text));
    }

    private Map<String, Object> message(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
