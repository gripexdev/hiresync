package ma.hiresync.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Translates controller/service exceptions into clean JSON responses with the
 * correct HTTP status. Without this, an uncaught exception is forwarded to
 * /error, which re-enters the stateless security chain and gets masked as a 403
 * — making the frontend believe the session expired.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Bad input from the client (validation, illegal arguments). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleBadRequest(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Operation not valid in the current state (e.g. boosting an unfinished optimization). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleConflict(IllegalStateException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Generic runtime errors. "... not found" messages map to 404 so the client
     * can react correctly; everything else is a genuine 500.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntime(RuntimeException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        if (msg.toLowerCase().contains("not found")) {
            return body(HttpStatus.NOT_FOUND, msg);
        }
        log.error("Unhandled runtime exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue.");
    }

    private ResponseEntity<Object> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", status.value(),
            "error", status.getReasonPhrase(),
            "message", message == null ? "" : message
        ));
    }
}
