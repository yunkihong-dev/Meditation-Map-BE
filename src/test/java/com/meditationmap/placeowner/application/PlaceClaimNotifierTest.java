package com.meditationmap.placeowner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.meditationmap.identity.application.MemberDirectory;
import com.meditationmap.notification.application.NotificationCommandService;
import com.meditationmap.notification.application.NotificationDraft;
import com.meditationmap.notification.domain.LinkUrlPolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 알림을 누르면 그 상세로 간다(사용자 요구, 라운드 3) — linkUrl 이 신청 단건 경로이고 LinkUrlPolicy 를 통과한다. */
@ExtendWith(MockitoExtension.class)
class PlaceClaimNotifierTest {

    @Mock private NotificationCommandService notifications;
    @Mock private MemberDirectory memberDirectory;

    @Test
    @DisplayName("반려 알림 linkUrl = /profile/place-claims/{claimId}, 승인 = /profile/places/{placeId}/edit")
    void 링크() {
        PlaceClaimNotifier n = new PlaceClaimNotifier(notifications, memberDirectory);
        ArgumentCaptor<NotificationDraft> d = ArgumentCaptor.forClass(NotificationDraft.class);

        n.rejected(58L, "m2", "고요명상센터", "사유");
        n.approved(57L, "m1", "12", "고요명상센터");

        verify(notifications, org.mockito.Mockito.times(2)).notify(org.mockito.ArgumentMatchers.anyCollection(), d.capture());
        assertThat(d.getAllValues().get(0).linkUrl()).isEqualTo("/profile/place-claims/58");
        assertThat(d.getAllValues().get(1).linkUrl()).isEqualTo("/profile/places/12/edit");
        assertThat(d.getAllValues()).allMatch(x -> LinkUrlPolicy.isAllowed(x.linkUrl()));
        verify(notifications).notify(eq(List.of("m2")), eq(d.getAllValues().get(0)));
    }
}
