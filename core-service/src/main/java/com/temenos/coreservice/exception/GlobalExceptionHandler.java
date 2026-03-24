package com.temenos.coreservice.exception;

import com.temenos.coreservice.model.ErrorCode;
import com.temenos.coreservice.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== VALIDATION ====================
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        logger.debug("Validation error: {}", message);
        return ResponseEntity.badRequest().body(buildError(message, ErrorCode.INVALID_PARAMETERS));
    }

    // ==================== NOT FOUND ====================
    @ExceptionHandler(TimerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(TimerNotFoundException ex) {
        logger.debug("Timer not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildError(ex.getMessage(), ErrorCode.ERROR));
    }

    // ==================== INVALID INPUT ====================
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInputException(ServerWebInputException ex) {
        logger.debug("Invalid input: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(buildError("Invalid request input", ErrorCode.INVALID_PARAMETERS));
    }

    // ==================== GENERIC ====================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Internal server error", ErrorCode.ERROR));
    }

    // ==================== INVALID UUID ====================
//    @ExceptionHandler(IllegalArgumentException.class)
//    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
//        logger.debug("Invalid argument: {}", ex.getMessage());
//        return ResponseEntity.badRequest().body(buildError("Invalid UUID format", ErrorCode.INVALID_PARAMETERS));
//    }


    private ErrorResponse buildError(String message, ErrorCode code) {
        ErrorResponse error = new ErrorResponse();
        error.setMessage(message);
        error.setCode(code);
        return error;
    }
}