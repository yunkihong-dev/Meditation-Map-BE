package com.meditationmap.placeowner.application;

import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimJpaEntity;
import com.meditationmap.placeowner.infrastructure.jpa.PlaceClaimSpringDataRepository;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import com.meditationmap.storage.application.port.out.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 증빙 조회 (계약 5.5). S3 비공개 키에서 BE 가 읽어 바이트로 돌려준다. 공개 URL 은 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PlaceEvidenceService {

    private final PlaceClaimSpringDataRepository claimRepository;
    private final ObjectProvider<ObjectStoragePort> storage;

    @Transactional(readOnly = true)
    public EvidenceBytes load(String claimIdRaw) {
        PlaceClaimJpaEntity c =
                claimRepository
                        .findById(PlaceClaimQueryService.parseClaimId(claimIdRaw))
                        .orElseThrow(() -> new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!c.hasEvidence()) {
            throw new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ObjectStoragePort port = storage.getIfAvailable();
        if (port == null) {
            throw new DomainArgumentException(ErrorCode.FILE_STORAGE_DISABLED);
        }
        ObjectStoragePort.ObjectContent content;
        try {
            content = port.get(c.getEvidenceObjectKey());
        } catch (RuntimeException e) {
            throw new InfrastructureException(ErrorCode.EXTERNAL_SERVICE_ERROR, e);
        }
        if (content == null) {
            throw new DomainArgumentException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String type = c.getEvidenceContentType() != null ? c.getEvidenceContentType() : content.contentType();
        return new EvidenceBytes(content.bytes(), type, c.getEvidenceFileName());
    }

    public record EvidenceBytes(byte[] bytes, String contentType, String fileName) {
        public boolean isImage() {
            return contentType != null && contentType.startsWith("image/");
        }
    }
}
