package com.meditationmap.platform.web;

import com.meditationmap.identity.application.AuthApplicationService;
import com.meditationmap.identity.domain.DuplicateEmailException;
import com.meditationmap.identity.domain.InvalidOAuthSignupTokenException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidOAuthSignupTokenException.class)
    public ResponseEntity<Map<String, String>> invalidOAuthSignupToken() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_OAUTH_SIGNUP_TOKEN"));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "EMAIL_ALREADY_EXISTS"));
    }

    @ExceptionHandler({
        BadCredentialsException.class,
        AuthApplicationService.InvalidCredentialsException.class
    })
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        var fields =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(
                                Collectors.toMap(
                                        FieldError::getField,
                                        e -> e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage(),
                                        (a, b) -> a));
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_FAILED", "fields", fields));
    }
}
