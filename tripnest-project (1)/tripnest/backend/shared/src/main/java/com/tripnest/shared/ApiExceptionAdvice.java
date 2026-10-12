package com.tripnest.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionAdvice {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex) {
    Map<String,String> fields = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) fields.putIfAbsent(error.getField(), error.getDefaultMessage());
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more fields are invalid", fields);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String,Object>> unreadable(HttpMessageNotReadableException ex) {
    return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body or parameter format is invalid", Map.of());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String,Object>> illegalArgument(IllegalArgumentException ex) {
    return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), Map.of());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String,Object>> status(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    return response(status, "REQUEST_FAILED", ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(), Map.of());
  }

  private static ResponseEntity<Map<String,Object>> response(HttpStatus status, String code, String message, Map<String,?> details) {
    Map<String,Object> body = new LinkedHashMap<>(); body.put("error", code); body.put("message", message); body.put("details", details);
    return ResponseEntity.status(status).body(body);
  }
}
