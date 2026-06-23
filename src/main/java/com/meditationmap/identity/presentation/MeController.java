package com.meditationmap.identity.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.meditationmap.identity.application.MemberProfileApplicationService;
import com.meditationmap.identity.application.ProfileImageAttachmentApplicationService;
import com.meditationmap.identity.domain.MemberId;
import com.meditationmap.identity.infrastructure.jpa.MemberJpaEntity;
import com.meditationmap.identity.infrastructure.jpa.MemberSpringDataRepository;
import com.meditationmap.identity.presentation.dto.MeResponse;
import com.meditationmap.identity.presentation.dto.ExpertProfileUpdateRequest;
import com.meditationmap.identity.presentation.dto.ProfileUpdateRequest;
import com.meditationmap.storage.application.FileUploadService;
import com.meditationmap.storage.infrastructure.minio.PublicMediaUrlResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Me")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final MemberSpringDataRepository memberRepository;
    private final ProfileImageAttachmentApplicationService profileImages;
    private final MemberProfileApplicationService memberProfiles;
    private final PublicMediaUrlResolver mediaUrlResolver;
    private final ObjectProvider<FileUploadService> fileUploadService;

    @Operation(summary = "내 정보 (JWT 또는 mm_access_token 쿠키)")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UserDetails user) {
        MemberJpaEntity member = findMember(user.getUsername());
        String role =
                user.getAuthorities().stream()
                        .findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                        .orElse("MEMBER");
        return toResponse(member, user.getUsername(), role);
    }

    @Operation(summary = "대표 프로필 사진 교체")
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeResponse updateProfileImage(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam("file") MultipartFile file) {
        FileUploadService upload = fileUploadService.getIfAvailable();
        if (upload == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "파일 저장소가 비활성화되어 있습니다.");
        }
        MemberJpaEntity member = findMember(user.getUsername());
        var uploaded = upload.upload(file);
        profileImages.replacePrimary(MemberId.of(member.getId()), uploaded.objectKey());
        memberProfiles.syncExpertAvatar(member.getId(), uploaded.url());
        return toResponse(member, user.getUsername(), resolveRole(user));
    }

    @Operation(summary = "일반 회원 프로필 수정")
    @PutMapping("/me/profile")
    public MeResponse updateProfile(
            @AuthenticationPrincipal UserDetails user,
            @Valid @org.springframework.web.bind.annotation.RequestBody ProfileUpdateRequest body) {
        MemberJpaEntity member = findMember(user.getUsername());
        memberProfiles.updateProfile(member.getId(), body);
        return toResponse(member, user.getUsername(), resolveRole(user));
    }

    @Operation(summary = "내 전문가 프로필 조회")
    @GetMapping("/me/expert-profile")
    public ResponseEntity<JsonNode> expertProfile(@AuthenticationPrincipal UserDetails user) {
        MemberJpaEntity member = findMember(user.getUsername());
        return memberProfiles
                .findExpert(member.getId())
                .map(com.meditationmap.expert.infrastructure.jpa.ExpertJpaEntity::getData)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "명상 전문가 전환 또는 전문가 프로필 수정")
    @PutMapping("/me/expert-profile")
    public MeResponse updateExpertProfile(
            @AuthenticationPrincipal UserDetails user,
            @Valid
                    @org.springframework.web.bind.annotation.RequestBody
                    ExpertProfileUpdateRequest body) {
        MemberJpaEntity member = findMember(user.getUsername());
        String avatarUrl =
                profileImages
                        .findPrimaryObjectKey(MemberId.of(member.getId()))
                        .map(this::publicUrlFor)
                        .orElse(null);
        memberProfiles.upsertExpert(member.getId(), avatarUrl, body);
        return toResponse(member, user.getUsername(), resolveRole(user));
    }

    private MeResponse toResponse(MemberJpaEntity member, String username, String role) {
        String imageUrl =
                profileImages
                        .findPrimaryObjectKey(MemberId.of(member.getId()))
                        .map(this::publicUrlFor)
                        .orElse(null);
        var profile = memberProfiles.findProfile(member.getId());
        String expertId =
                memberProfiles.findExpert(member.getId()).map(e -> e.getId()).orElse(null);
        return new MeResponse(
                username,
                member.getEmail(),
                role,
                imageUrl,
                member.getCreatedAt(),
                profile.displayName(),
                profile.regionIds(),
                profile.interests(),
                expertId);
    }

    private MemberJpaEntity findMember(String username) {
        return memberRepository
                .findByLoginId(username)
                .or(() -> memberRepository.findByEmail(username))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."));
    }

    private String resolveRole(UserDetails user) {
        return user.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .orElse("MEMBER");
    }

    private String publicUrlFor(String objectKey) {
        return mediaUrlResolver.urlFor(objectKey);
    }
}
