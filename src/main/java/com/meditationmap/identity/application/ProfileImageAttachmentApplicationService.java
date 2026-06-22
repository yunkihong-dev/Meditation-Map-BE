package com.meditationmap.identity.application;

import com.meditationmap.identity.domain.MemberId;
import com.meditationmap.identity.infrastructure.jpa.MemberProfileImageJpaEntity;
import com.meditationmap.identity.infrastructure.jpa.MemberProfileImageSpringDataRepository;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.storage.infrastructure.jpa.StoredFileJpaEntity;
import com.meditationmap.storage.infrastructure.jpa.StoredFileSpringDataRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileImageAttachmentApplicationService {

    private static final int MAX_OBJECT_KEY_LENGTH = 512;

    private final StoredFileSpringDataRepository storedFileRepo;
    private final MemberProfileImageSpringDataRepository memberProfileImageRepo;

    /** 회원가입 직후 대표 프로필 이미지로 연결합니다. objectKey 는 업로드 API가 반환한 값과 동일해야 합니다. */
    @Transactional
    public void attachPrimaryOnSignup(MemberId memberId, String profileImageObjectKey) {
        if (!StringUtils.hasText(profileImageObjectKey)) {
            return;
        }
        String key = profileImageObjectKey.trim();
        validateObjectKey(key);

        StoredFileJpaEntity file =
                storedFileRepo
                        .findByObjectKey(key)
                        .orElseGet(() -> persistNewStoredFilePlaceholder(key));

        memberProfileImageRepo.clearPrimaryForMember(memberId.value());

        MemberProfileImageJpaEntity link = new MemberProfileImageJpaEntity();
        link.setId(UUID.randomUUID().toString());
        link.setMemberId(memberId.value());
        link.setFileId(file.getId());
        link.setPrimaryImage(true);
        link.setSortOrder(0);
        memberProfileImageRepo.save(link);
    }

    @Transactional(readOnly = true)
    public Optional<String> findPrimaryObjectKey(MemberId memberId) {
        return memberProfileImageRepo
                .findFirstByMemberIdAndPrimaryImageTrueOrderBySortOrderAsc(memberId.value())
                .flatMap(link -> storedFileRepo.findById(link.getFileId()))
                .map(StoredFileJpaEntity::getObjectKey);
    }

    /** 마이페이지에서 업로드한 이미지를 새 대표 이미지로 교체합니다. */
    @Transactional
    public void replacePrimary(MemberId memberId, String profileImageObjectKey) {
        attachPrimaryOnSignup(memberId, profileImageObjectKey);
    }

    private static void validateObjectKey(String key) {
        if (key.length() > MAX_OBJECT_KEY_LENGTH) {
            throw new DomainArgumentException(ErrorCode.INVALID_PROFILE_IMAGE_OBJECT_KEY);
        }
        if (key.contains("..") || key.startsWith("/") || key.contains("\\")) {
            throw new DomainArgumentException(ErrorCode.INVALID_PROFILE_IMAGE_OBJECT_KEY);
        }
        if (!key.startsWith("uploads/")) {
            throw new DomainArgumentException(ErrorCode.INVALID_PROFILE_IMAGE_OBJECT_KEY);
        }
    }

    private StoredFileJpaEntity persistNewStoredFilePlaceholder(String objectKey) {
        StoredFileJpaEntity e = new StoredFileJpaEntity();
        e.setId(UUID.randomUUID().toString());
        e.setObjectKey(objectKey);
        e.setCreatedAt(Instant.now());
        return storedFileRepo.save(e);
    }
}
