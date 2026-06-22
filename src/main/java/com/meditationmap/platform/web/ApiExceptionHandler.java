package com.meditationmap.platform.web;

import com.meditationmap.platform.web.error.ApiErrorResponse;
import com.meditationmap.shared.domain.DomainException;
import com.meditationmap.shared.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> domain(DomainException ex) {
        ErrorCode ec = ex.getErrorCode();
        log.debug("DomainException {}", ec.code(), ex);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiErrorResponse.from(ec));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(
                                Collectors.toMap(
                                        org.springframework.validation.FieldError::getField,
                                        fe ->
                                                fe.getDefaultMessage() == null
                                                        ? ""
                                                        : fe.getDefaultMessage(),
                                        (a, b) -> a));
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(
                        ApiErrorResponse.from(
                                ErrorCode.VALIDATION_FAILED, Map.of("fields", fields)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> violations = new HashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            violations.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(
                        ApiErrorResponse.from(
                                ErrorCode.VALIDATION_FAILED, Map.of("constraints", violations)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> notReadable(HttpMessageNotReadableException ex) {
        log.debug("Bad request body", ex);
        return body(ErrorCode.INVALID_REQUEST_BODY);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Object value = ex.getValue();
        Map<String, Object> detail = new HashMap<>();
        detail.put("parameter", name != null ? name : "");
        detail.put("value", value != null ? String.valueOf(value) : "");
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getHttpStatus())
                .body(ApiErrorResponse.from(ErrorCode.INVALID_PARAMETER, detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> missingParam(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.status(ErrorCode.MISSING_REQUIRED_PARAMETER.getHttpStatus())
                .body(
                        ApiErrorResponse.from(
                                ErrorCode.MISSING_REQUIRED_PARAMETER,
                                Map.of("parameter", ex.getParameterName())));
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiErrorResponse> missingPathVar(MissingPathVariableException ex) {
        return ResponseEntity.status(ErrorCode.MISSING_PATH_VARIABLE.getHttpStatus())
                .body(
                        ApiErrorResponse.from(
                                ErrorCode.MISSING_PATH_VARIABLE,
                                Map.of("variable", ex.getVariableName())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> noResource(NoResourceFoundException ex) {
        log.debug("No handler for {}", ex.getResourcePath());
        return body(ErrorCode.ENDPOINT_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> methodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String[] raw = ex.getSupportedMethods();
        String supported =
                raw == null || raw.length == 0 ? "" : String.join(", ", raw);
        return ResponseEntity.status(ErrorCode.ENDPOINT_METHOD_NOT_ALLOWED.getHttpStatus())
                .body(
                        ApiErrorResponse.from(
                                ErrorCode.ENDPOINT_METHOD_NOT_ALLOWED,
                                Map.of("method", ex.getMethod(), "supportedMethods", supported)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> mediaNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.debug("Unsupported media type", ex);
        return body(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> uploadTooLarge(MaxUploadSizeExceededException ex) {
        log.debug("Upload exceeds limit", ex);
        return body(ErrorCode.UPLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> multipart(MultipartException ex) {
        log.warn("Multipart processing failed", ex);
        ErrorCode fallback = awsRootCause(ex);
        ErrorCode ec = fallback != null ? fallback : ErrorCode.FILE_REQUIRED;
        return body(ec);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> authentication(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) {
            return body(ErrorCode.INVALID_CREDENTIALS);
        }
        log.debug("Authentication failed", ex);
        return body(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException ex) {
        log.debug("Access denied", ex);
        return body(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> dataAccess(DataAccessException ex) {
        log.error("Persistence error", ex);
        return body(ErrorCode.DATA_ACCESS_FAILED);
    }

    /**
     * 기존 {@link IllegalArgumentException}/{@link IllegalStateException} 문구 또는 예외 원인 체인을 사용해 매핑합니다.
     */
    @ExceptionHandler({
        IllegalStateException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> legacyIllegal(RuntimeException ex) {
        ErrorCode byChain = awsRootCause(ex);
        if (byChain != null) {
            log.warn("{} — AWS/S3 관련 원인 매핑", ex.getMessage());
            return body(byChain);
        }

        ErrorCode byMessage = mapLegacyMessage(ex.getMessage(), ex.getCause());
        if (byMessage != null) {
            log.warn("{} — 레거시 메시지 매핑: {}", ex.toString(), byMessage.code());
            return body(byMessage);
        }

        log.error("예기치 않은 IllegalArgument/IllegalState — ErrorCode 매핑 권장", ex);
        return body(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> fallback(Exception ex) {
        ErrorCode byChain = awsRootCause(ex);
        if (byChain != null) {
            log.warn("원인 체인을 외부 서비스 오류로 매핑했습니다.", ex);
            return body(byChain);
        }

        log.error("처리되지 않은 예외", ex);
        return body(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ApiErrorResponse> body(ErrorCode ec) {
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiErrorResponse.from(ec));
    }

    private static ErrorCode awsRootCause(Throwable ex) {
        Throwable c = ex;
        int i = 0;
        while (c != null && i++ < 12) {
            if (c instanceof S3Exception
                    || c instanceof SdkClientException
                    || c instanceof SdkServiceException) {
                return ErrorCode.EXTERNAL_SERVICE_ERROR;
            }
            c = c.getCause();
        }
        return null;
    }

    /** @return 알 수 없으면 null → INTERNAL 또는 상위 처리 */
    private static ErrorCode mapLegacyMessage(String message, Throwable cause) {
        ErrorCode infra = awsRootCause(cause);
        if (infra != null) {
            return infra;
        }
        if (message == null) {
            return null;
        }
        if (message.startsWith("expert serialize failed")) {
            return ErrorCode.EXPERT_SERIALIZATION_FAILED;
        }
        if (message.startsWith("place serialize failed")) {
            return ErrorCode.PLACE_SERIALIZATION_FAILED;
        }
        if (message.startsWith("expert json corrupt")) {
            return ErrorCode.EXPERT_PAYLOAD_INVALID;
        }
        if (message.startsWith("place json corrupt")) {
            return ErrorCode.PLACE_PAYLOAD_INVALID;
        }
        if ("member not found".equalsIgnoreCase(message)) {
            return ErrorCode.MEMBER_NOT_FOUND;
        }
        if ("failed to store file".equals(message)) {
            return ErrorCode.FILE_STORE_FAILED;
        }
        return null;
    }
}
