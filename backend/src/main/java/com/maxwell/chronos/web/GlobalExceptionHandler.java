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
                .message("The profile request could not be read. Check the date and photo, then try again.")
                .status(HttpStatus.BAD_REQUEST.value())
                .build());
    }
}
