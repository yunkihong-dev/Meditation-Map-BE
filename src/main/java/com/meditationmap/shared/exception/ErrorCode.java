package com.meditationmap.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 오류 코드. 클라이언트에는 {@link #code()} 문자열과 한글 {@link #getMessage()}로 노출합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_OAUTH_SIGNUP_TOKEN(HttpStatus.BAD_REQUEST,"유효하지 않거나 만료된 OAuth 가입 토큰입니다. 초기 회원가입 절차를 다시 시작해 주세요."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "유효한 휴대전화번호 형식이 아닙니다. 예: 01012345678"),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 전화번호입니다."),
    PHONE_OTP_SEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증 문자를 너무 자주 요청했습니다. 잠시 후 다시 시도해 주세요."),
    PHONE_OTP_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않거나 만료되었습니다."),
    PHONE_VERIFY_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "휴대전화 인증이 완료되지 않았거나 만료되었습니다. 문자 인증을 다시 진행해 주세요."),

    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "유효한 이메일 형식이 아닙니다."),
    INVALID_PROFILE_IMAGE_OBJECT_KEY(
            HttpStatus.BAD_REQUEST,
            "프로필 이미지 식별값이 올바르지 않습니다. 업로드 응답의 objectKey 를 그대로 보내 주세요."),
    INVALID_MEMBER_ID(HttpStatus.BAD_REQUEST, "회원 식별자 형식이 올바르지 않습니다."),
    INVALID_PLACE_ID(HttpStatus.BAD_REQUEST, "장소 식별자 형식이 올바르지 않습니다."),
    INVALID_REGION_ID(HttpStatus.BAD_REQUEST, "지역 식별자 형식이 올바르지 않습니다."),
    INVALID_EXPERT_ID(HttpStatus.BAD_REQUEST, "전문가 식별자 형식이 올바르지 않습니다."),

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST,"요청 본문(JSON 등) 형식이 올바르지 않거나 서버에서 읽을 수 없습니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터 값이 유효하지 않습니다."),
    MISSING_REQUIRED_PARAMETER(HttpStatus.BAD_REQUEST, "필수 요청 파라미터가 누락되었습니다."),
    MISSING_PATH_VARIABLE(HttpStatus.BAD_REQUEST, "필수 경로 변수가 누락되었습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다. 로그인 후 다시 시도해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 리소스에 접근할 권한이 없습니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 API 경로입니다."),
    ENDPOINT_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type 또는 Accept 입니다."),

    EXPERT_PAYLOAD_INVALID(HttpStatus.INTERNAL_SERVER_ERROR,"저장된 전문가 데이터 형식(JSON)에 문제가 있습니다. 관리자에게 문의해 주세요."),
    PLACE_PAYLOAD_INVALID(HttpStatus.INTERNAL_SERVER_ERROR,"저장된 장소 데이터 형식(JSON)에 문제가 있습니다. 관리자에게 문의해 주세요."),
    EXPERT_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "전문가 데이터 처리(직렬화) 중 오류가 발생했습니다."),
    PLACE_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "장소 데이터 처리(직렬화) 중 오류가 발생했습니다."),

    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "업로드할 파일이 필요합니다."),
    FILE_STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,"파일 저장 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    UPLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "허용된 업로드 용량을 초과했습니다. 파일 크기를 줄여 주세요."),

    DATA_ACCESS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "저장소에 접근하는 중 오류가 발생했습니다."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스(스토리지 등) 호출 중 오류가 발생했습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 예기치 않은 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    ;

    private final HttpStatus httpStatus;

    /** 사용자·프론트에 보여 줄 한글 설명 */
    private final String message;

    /** API 응답의 machine-readable 코드 문자열 */
    public String code() {
        return name();
    }
}
