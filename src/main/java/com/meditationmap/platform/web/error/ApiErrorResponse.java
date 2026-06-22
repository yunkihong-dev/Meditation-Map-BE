package com.meditationmap.platform.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.Map;

/**
 * API 오류 통일 응답.
 *
 * @param code 기계가 읽는 오류 코드({@link ErrorCode#code()})
 * @param message 사용자용 한글 메시지
 * @param details 검증 필드류 등 부가 정보(선택)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message, Map<String, Object> details) {

    public static ApiErrorResponse from(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.code(), errorCode.getMessage(), null);
    }

    public static ApiErrorResponse from(ErrorCode errorCode, Map<String, Object> details) {
        return new ApiErrorResponse(errorCode.code(), errorCode.getMessage(), details);
    }
}
