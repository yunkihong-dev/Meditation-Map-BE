package com.meditationmap.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.expert.infrastructure.jpa.ExpertJpaEntity;
import com.meditationmap.expert.infrastructure.jpa.ExpertSpringDataRepository;
import com.meditationmap.banner.infrastructure.jpa.BannerJpaEntity;
import com.meditationmap.banner.infrastructure.jpa.BannerSpringDataRepository;
import com.meditationmap.notice.infrastructure.jpa.NoticeJpaEntity;
import com.meditationmap.notice.infrastructure.jpa.NoticeSpringDataRepository;
import com.meditationmap.place.application.PlaceProgramNormalizer;
import com.meditationmap.place.infrastructure.jpa.PlaceJpaEntity;
import com.meditationmap.place.infrastructure.jpa.PlaceSpringDataRepository;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminCatalogService {

    private final PlaceSpringDataRepository placeRepo;
    private final ExpertSpringDataRepository expertRepo;
    private final NoticeSpringDataRepository noticeRepo;
    private final BannerSpringDataRepository bannerRepo;

    @Transactional(readOnly = true)
    public List<PlaceJpaEntity> listPlaces() {
        return placeRepo.findAll();
    }

    @Transactional(readOnly = true)
    public PlaceJpaEntity getPlace(String id) {
        return placeRepo.findById(id).orElseThrow(() -> new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @CacheEvict(value = {"places", "place"}, allEntries = true)
    public PlaceJpaEntity createPlace(String regionId, JsonNode data) {
        validateRegionId(regionId);
        String id = AdminNumericIdGenerator.nextId(placeRepo.findAll().stream().map(PlaceJpaEntity::getId).toList());
        return savePlace(id, regionId, data);
    }

    @CacheEvict(value = {"places", "place"}, allEntries = true)
    public PlaceJpaEntity updatePlace(String id, String regionId, JsonNode data) {
        validateId(id);
        validateRegionId(regionId);
        if (!placeRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return savePlace(id, regionId, data);
    }

    @CacheEvict(value = {"places", "place"}, allEntries = true)
    public void deletePlace(String id) {
        if (!placeRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        placeRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ExpertJpaEntity> listExperts() {
        return expertRepo.findAll();
    }

    @Transactional(readOnly = true)
    public ExpertJpaEntity getExpert(String id) {
        return expertRepo.findById(id).orElseThrow(() -> new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @CacheEvict(value = {"experts", "expert"}, allEntries = true)
    public ExpertJpaEntity createExpert(JsonNode data) {
        String id = AdminNumericIdGenerator.nextId(expertRepo.findAll().stream().map(ExpertJpaEntity::getId).toList());
        return saveExpert(id, data);
    }

    @CacheEvict(value = {"experts", "expert"}, allEntries = true)
    public ExpertJpaEntity updateExpert(String id, JsonNode data) {
        validateId(id);
        if (!expertRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return saveExpert(id, data);
    }

    @CacheEvict(value = {"experts", "expert"}, allEntries = true)
    public void deleteExpert(String id) {
        if (!expertRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        expertRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<NoticeJpaEntity> listNotices() {
        return noticeRepo.findAll();
    }

    @CacheEvict(value = "notices", allEntries = true)
    public NoticeJpaEntity createNotice(JsonNode payload) {
        String id = AdminNumericIdGenerator.nextId(noticeRepo.findAll().stream().map(NoticeJpaEntity::getId).toList());
        return saveNotice(id, payload);
    }

    @CacheEvict(value = "notices", allEntries = true)
    public NoticeJpaEntity updateNotice(String id, JsonNode payload) {
        validateId(id);
        if (!noticeRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return saveNotice(id, payload);
    }

    @CacheEvict(value = "notices", allEntries = true)
    public void deleteNotice(String id) {
        if (!noticeRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        noticeRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<BannerJpaEntity> listBanners() {
        return bannerRepo.findAll();
    }

    @CacheEvict(value = "banners", allEntries = true)
    public BannerJpaEntity createBanner(JsonNode payload) {
        String id = AdminNumericIdGenerator.nextId(bannerRepo.findAll().stream().map(BannerJpaEntity::getId).toList());
        return saveBanner(id, payload);
    }

    @CacheEvict(value = "banners", allEntries = true)
    public BannerJpaEntity updateBanner(String id, JsonNode payload) {
        validateId(id);
        if (!bannerRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return saveBanner(id, payload);
    }

    @CacheEvict(value = "banners", allEntries = true)
    public void deleteBanner(String id) {
        if (!bannerRepo.existsById(id)) {
            throw new InfrastructureException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        bannerRepo.deleteById(id);
    }

    private PlaceJpaEntity savePlace(String id, String regionId, JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new InfrastructureException(ErrorCode.INVALID_REQUEST_BODY);
        }
        ObjectNode merged = ((ObjectNode) data.deepCopy());
        merged.put("id", id);
        merged.put("regionId", regionId);
        PlaceProgramNormalizer.normalizePlaceData(merged);

        PlaceJpaEntity entity = placeRepo.findById(id).orElseGet(PlaceJpaEntity::new);
        entity.setId(id);
        entity.setRegionId(regionId);
        entity.setData(merged);
        return placeRepo.save(entity);
    }

    private ExpertJpaEntity saveExpert(String id, JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new InfrastructureException(ErrorCode.INVALID_REQUEST_BODY);
        }
        ObjectNode merged = ((ObjectNode) data.deepCopy());
        merged.put("id", id);

        ExpertJpaEntity entity = expertRepo.findById(id).orElseGet(ExpertJpaEntity::new);
        entity.setId(id);
        entity.setData(merged);
        return expertRepo.save(entity);
    }

    private NoticeJpaEntity saveNotice(String id, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new InfrastructureException(ErrorCode.INVALID_REQUEST_BODY);
        }
        ObjectNode merged = ((ObjectNode) payload.deepCopy());
        merged.put("id", id);

        NoticeJpaEntity entity = noticeRepo.findById(id).orElseGet(NoticeJpaEntity::new);
        entity.setId(id);
        entity.setPayload(merged);
        return noticeRepo.save(entity);
    }

    /**
     * 배너 저장. createdAt 은 처음 만들 때 한 번만 찍고 그대로 둡니다 —
     * "언제 올린 배너인지" 는 나중에 고쳤다고 바뀌면 안 되는 값이라서입니다.
     */
    private BannerJpaEntity saveBanner(String id, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new InfrastructureException(ErrorCode.INVALID_REQUEST_BODY);
        }
        ObjectNode merged = ((ObjectNode) payload.deepCopy());
        merged.put("id", id);

        BannerJpaEntity entity = bannerRepo.findById(id).orElseGet(BannerJpaEntity::new);
        String now = java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul")).toString();
        JsonNode before = entity.getPayload();
        String createdAt =
                before != null && before.hasNonNull("createdAt") ? before.get("createdAt").asText(now) : now;
        merged.put("createdAt", createdAt);
        merged.put("updatedAt", now);

        entity.setId(id);
        entity.setPayload(merged);
        return bannerRepo.save(entity);
    }

    private static void validateId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new InfrastructureException(ErrorCode.INVALID_REQUEST_BODY);
        }
    }

    private static void validateRegionId(String regionId) {
        if (!StringUtils.hasText(regionId)) {
            throw new InfrastructureException(ErrorCode.INVALID_REQUEST_BODY);
        }
    }
}
