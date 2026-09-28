package com.example.equity.web;

import com.example.equity.exercise.ExceedsExercisableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ExceedsExercisableException.class)
    public ResponseEntity<Map<String, Object>> handleExceeds(ExceedsExercisableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(
                "EXCEEDS_EXERCISABLE", ex.getMessage(),
                Map.of("requested", ex.getRequested(), "available", ex.getAvailable(), "date", ex.getDate())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(body("BAD_REQUEST", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("CONFLICT", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(body("VALIDATION_FAILED", "请求参数校验失败", Map.of("fields", fields)));
    }

    private Map<String, Object> body(String code, String message, Map<String, ?> details) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", OffsetDateTime.now().toString());
        map.put("code", code);
        map.put("message", message);
        map.putAll(details);
        return map;
    }
}
