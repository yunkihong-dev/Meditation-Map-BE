package com.meditationmap.notification.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.MessagingErrorCode;
import com.meditationmap.notification.application.port.out.PushSender.PushResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FCM 오류 → 토큰 삭제 여부 (계약 2.7, QA Q6). TOKEN_INVALID 면 호출자가 토큰을 지운다.
 *
 * <p>보장: UNREGISTERED 와 "등록 토큰이 잘못됨" INVALID_ARGUMENT 만 삭제 대상 · 그 밖의 INVALID_ARGUMENT(링크·payload)와
 * 일시 오류는 토큰 유지. 보장하지 않음: FCM 이 실제로 돌려주는 메시지 문구(SDK·서버 버전에 따라 바뀔 수 있다).
 */
class FcmPushSenderClassifyTest {

    @Test
    @DisplayName("UNREGISTERED 는 토큰 삭제")
    void unregistered() {
        assertThat(FcmPushSender.classify(MessagingErrorCode.UNREGISTERED, "Requested entity was not found."))
                .isEqualTo(PushResult.TOKEN_INVALID);
    }

    @Test
    @DisplayName("등록 토큰이 잘못된 INVALID_ARGUMENT 는 토큰 삭제")
    void 잘못된_등록_토큰() {
        assertThat(FcmPushSender.classify(
                        MessagingErrorCode.INVALID_ARGUMENT,
                        "The registration token is not a valid FCM registration token"))
                .isEqualTo(PushResult.TOKEN_INVALID);
    }

    @Test
    @DisplayName("토큰과 무관한 INVALID_ARGUMENT(링크가 https 아님 등)는 토큰을 지우지 않는다")
    void 토큰_무관_INVALID_ARGUMENT() {
        assertThat(FcmPushSender.classify(
                        MessagingErrorCode.INVALID_ARGUMENT,
                        "Request contains an invalid argument. WebpushFcmOptions.link must be a HTTPS URL"))
                .isEqualTo(PushResult.FAILED);
        assertThat(FcmPushSender.classify(MessagingErrorCode.INVALID_ARGUMENT, null)).isEqualTo(PushResult.FAILED);
    }

    @Test
    @DisplayName("일시·설정 오류는 토큰 유지")
    void 그_외_오류() {
        for (MessagingErrorCode c : new MessagingErrorCode[] {
            MessagingErrorCode.QUOTA_EXCEEDED, MessagingErrorCode.UNAVAILABLE, MessagingErrorCode.INTERNAL,
            MessagingErrorCode.SENDER_ID_MISMATCH, MessagingErrorCode.THIRD_PARTY_AUTH_ERROR
        }) {
            assertThat(FcmPushSender.classify(c, "registration token")).as(c.name()).isEqualTo(PushResult.FAILED);
        }
        assertThat(FcmPushSender.classify(null, "x")).isEqualTo(PushResult.FAILED);
    }
}
