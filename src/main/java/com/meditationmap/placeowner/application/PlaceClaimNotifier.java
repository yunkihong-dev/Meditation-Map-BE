package com.meditationmap.placeowner.application;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.NotificationCommandService;
import com.meditationmap.notification.application.NotificationDraft;
import com.meditationmap.notification.domain.NotificationRefType;
import com.meditationmap.notification.domain.NotificationType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 소유 신청 관련 알림 문구·링크 (계약 1절 표). linkUrl 은 계약 9절 FE 라우트와 정확히 같아야 한다.
 *
 * <p>호출자 트랜잭션에 합류해 저장되고, 전달은 커밋 뒤다.
 */
@Component
@RequiredArgsConstructor
public class PlaceClaimNotifier {

    static final String AUTO_REJECT_REASON = "다른 신청이 승인되었습니다";

    private final NotificationCommandService notifications;
    private final MemberDirectory memberDirectory;

    void submitted(Long claimId, String placeName, String applicantEmail) {
        List<String> staff = memberDirectory.staffMemberIds();
        notifications.notify(
                staff,
                new NotificationDraft(
                        NotificationType.PLACE_CLAIM_SUBMITTED,
                        "새 소유 신청이 들어왔어요",
                        "'" + nz(placeName) + "' — " + nz(applicantEmail),
                        "/admin/place-claims/" + claimId,
                        NotificationRefType.PLACE_CLAIM,
                        String.valueOf(claimId)));
    }

    void approved(Long claimId, String applicantMemberId, String placeId, String placeName) {
        notifications.notify(
                List.of(applicantMemberId),
                new NotificationDraft(
                        NotificationType.PLACE_CLAIM_APPROVED,
                        "소유 신청이 승인되었어요",
                        "'" + nz(placeName) + "'의 오너가 되었어요. 이제 장소 정보를 직접 수정할 수 있어요.",
                        "/profile/places/" + placeId + "/edit",
                        NotificationRefType.PLACE_CLAIM,
                        String.valueOf(claimId)));
    }

    void rejected(Long claimId, String applicantMemberId, String placeName, String reason) {
        notifications.notify(
                List.of(applicantMemberId),
                new NotificationDraft(
                        NotificationType.PLACE_CLAIM_REJECTED,
                        "소유 신청이 반려되었어요",
                        "'" + nz(placeName) + "' 신청이 반려되었어요. 사유: " + nz(reason),
                        "/profile/place-claims/" + claimId,
                        NotificationRefType.PLACE_CLAIM,
                        String.valueOf(claimId)));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
