package com.tenantrag.backend.config;

import com.tenantrag.backend.auth.AuthConflictException;
import com.tenantrag.backend.auth.InvalidCredentialsException;
import com.tenantrag.backend.chat.ConversationNotFoundException;
import com.tenantrag.backend.rag.RagServiceException;
import com.tenantrag.backend.document.DocumentNotFoundException;
import com.tenantrag.backend.document.InvalidUploadException;
import com.tenantrag.backend.document.StorageReadException;
import com.tenantrag.backend.storage.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates common exceptions into consistent JSON error payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(AuthConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            AuthConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(
            DocumentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleConversationNotFound(
            ConversationNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    /** LLM/embeddings upstream down or rate-limited → 503 so the UI can say "try again". */
    @ExceptionHandler(RagServiceException.class)
    public ResponseEntity<Map<String, Object>> handleRagService(RagServiceException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI service is busy or unavailable. Please try again shortly.", null);
    }

    @ExceptionHandler({InvalidUploadException.class, StorageReadException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidUpload(
            RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(
            MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the maximum allowed size.", null);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(
            StorageException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Storage error while processing the file.", null);
    }

    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String message, Object details) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}
