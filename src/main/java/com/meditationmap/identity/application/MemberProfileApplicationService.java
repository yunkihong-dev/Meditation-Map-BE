package com.meditationmap.identity.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.expert.infrastructure.jpa.ExpertJpaEntity;
import com.meditationmap.expert.infrastructure.jpa.ExpertSpringDataRepository;
import com.meditationmap.expert.infrastructure.jpa.ExpertVerificationJpaEntity;
import com.meditationmap.expert.infrastructure.jpa.ExpertVerificationSpringDataRepository;
import com.meditationmap.identity.infrastructure.jpa.MemberProfileJpaEntity;
import com.meditationmap.identity.infrastructure.jpa.MemberProfileSpringDataRepository;
import com.meditationmap.identity.presentation.dto.ExpertProfileUpdateRequest;
import com.meditationmap.identity.presentation.dto.ProfileUpdateRequest;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MemberProfileApplicationService {

    private final MemberProfileSpringDataRepository profileRepo;
    private final ExpertSpringDataRepository expertRepo;
    private final ExpertVerificationSpringDataRepository verificationRepo;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProfileData findProfile(String memberId) {
        return profileRepo
                .findById(memberId)
                .map(
                        p ->
                                new ProfileData(
                                        p.getDisplayName(),
                                        textList(p.getRegionIds()),
                                        textList(p.getInterests())))
                .orElseGet(() -> new ProfileData(null, List.of(), List.of()));
    }

    @Transactional
    public ProfileData updateProfile(String memberId, ProfileUpdateRequest request) {
        MemberProfileJpaEntity profile =
                profileRepo.findById(memberId).orElseGet(MemberProfileJpaEntity::new);
        profile.setMemberId(memberId);
        profile.setDisplayName(request.displayName().trim());
        profile.setRegionIds(toArray(request.regionIds()));
        profile.setInterests(toArray(request.interests()));
        profileRepo.save(profile);
        return findProfile(memberId);
    }

    @Transactional(readOnly = true)
    public Optional<ExpertJpaEntity> findExpert(String memberId) {
        return expertRepo.findByOwnerMemberId(memberId);
    }

    @Transactional
    @CacheEvict(value = {"experts", "expert"}, allEntries = true)
    public ExpertJpaEntity upsertExpert(
            String memberId, String avatarUrl, ExpertProfileUpdateRequest request) {
        ExpertJpaEntity expert =
                expertRepo.findByOwnerMemberId(memberId).orElseGet(ExpertJpaEntity::new);
        if (!StringUtils.hasText(expert.getId())) {
            expert.setId(memberId);
        }
        expert.setOwnerMemberId(memberId);

        ObjectNode data =
                expert.getData() != null && expert.getData().isObject()
                        ? ((ObjectNode) expert.getData()).deepCopy()
                        : objectMapper.createObjectNode();
        data.put("id", expert.getId());
        data.put("name", request.name().trim());
        data.put("intro", request.intro().trim());
        data.put("avatarUrl", avatarUrl != null ? avatarUrl : "");
        data.set("degrees", toArray(request.degrees()));
        data.set("certificates", toArray(request.certificates()));
        data.set("careers", toArray(request.careers()));
        data.set("classTypes", toArray(request.classTypes()));
        data.set("specialties", toArray(request.classTypes()));
        data.set("regionIds", toArray(request.regionIds()));
        data.set("activityAreas", toArray(request.regionIds()));
        data.put("hasCenter", request.hasCenter());
        if (request.hasCenter()) {
            data.put("centerSummary", trimToEmpty(request.centerName()));
            data.put("centerAddress", trimToEmpty(request.centerAddress()));
        } else {
            data.remove("centerSummary");
            data.remove("centerAddress");
            data.remove("centerPlaceId");
        }
        if (!data.has("programs")) data.set("programs", objectMapper.createArrayNode());
        if (!data.has("reviews")) data.set("reviews", objectMapper.createArrayNode());
        expert.setData(data);
        ExpertJpaEntity saved = expertRepo.save(expert);

        if (request.hasCenter()
                && (StringUtils.hasText(request.businessRegistrationNumber())
                        || request.businessOpeningDate() != null)) {
            ExpertVerificationJpaEntity verification =
                    verificationRepo
                            .findById(saved.getId())
                            .orElseGet(ExpertVerificationJpaEntity::new);
            verification.setExpertId(saved.getId());
            verification.setBusinessRegistrationNumber(
                    trimToNull(request.businessRegistrationNumber()));
            verification.setBusinessOpeningDate(request.businessOpeningDate());
            verification.setVerificationStatus("PENDING");
            verificationRepo.save(verification);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"experts", "expert"}, allEntries = true)
    public void syncExpertAvatar(String memberId, String avatarUrl) {
        expertRepo.findByOwnerMemberId(memberId)
                .ifPresent(
                        expert -> {
                            if (expert.getData() != null && expert.getData().isObject()) {
                                ObjectNode data = ((ObjectNode) expert.getData()).deepCopy();
                                data.put("avatarUrl", avatarUrl != null ? avatarUrl : "");
                                expert.setData(data);
                                expertRepo.save(expert);
                            }
                        });
    }

    private ArrayNode toArray(List<String> values) {
        ArrayNode out = objectMapper.createArrayNode();
        if (values == null) return out;
        values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .forEach(out::add);
        return out;
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .toList();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record ProfileData(String displayName, List<String> regionIds, List<String> interests) {}
}
