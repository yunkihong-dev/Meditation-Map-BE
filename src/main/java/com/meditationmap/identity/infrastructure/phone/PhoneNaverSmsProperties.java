package com.meditationmap.identity.infrastructure.phone;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sms-provider=naver 일 때 네이버 클라우드 플랫폼 SENS SMS(v2) API 로 발송합니다.
 *
 * <p>콘솔에서 등록한 SMS 서비스 ID·발신번호(API 인증 키 쌍과 매핑되는 프로젝트)가 필요합니다.
 */
@Data
@ConfigurationProperties(prefix = "app.auth.phone.naver")
public class PhoneNaverSmsProperties {

    /** SENS 콘솔 프로젝트의 SMS 서비스 ID (예: {@code ncp:sms:kr:...:sens}) */
    private String serviceId = "";

    /** 발신번호 — 콘솔에서 사전 등록된 번호만 사용 가능 */
    private String from = "";

    private String accessKey = "";
    private String secretKey = "";

    /** 기본 게이트웨이 호스트는 고정값 */
    private String baseUrl = "https://sens.apigw.ntruss.com";

    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 15_000;
}
