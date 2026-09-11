package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ErrorResponse.builder().error("Forbidden")
                .message(ex.getMessage()).status(403).build());
    }
    @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
    public ResponseEntity<ErrorResponse> handleInvalidInput(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder().error("Invalid Request")
                .message(ex.getMessage()).status(400).build());
    }

    @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class, org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(409).body(ErrorResponse.builder().error("Conflict")
                .message("The record changed or conflicts with existing data. Reload and try again.").status(409).build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Request validation failed";
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .error("Validation Failed")
                .message(message)
                .status(HttpStatus.BAD_REQUEST.value())
                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .error("Invalid Request")
                .message("The request could not be read. Check the field values and try again.")
                .status(HttpStatus.BAD_REQUEST.value())
                .build());
    }
}
