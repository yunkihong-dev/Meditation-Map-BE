package com.meditationmap.identity.application;

import com.meditationmap.identity.domain.MemberRole;
import com.meditationmap.identity.infrastructure.jpa.MemberJpaEntity;
import com.meditationmap.identity.infrastructure.jpa.MemberSpringDataRepository;
import com.meditationmap.shared.exception.DomainArgumentException;
import com.meditationmap.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 컨텍스트(notification·placeowner)가 회원을 "식별자 수준"으로만 보게 해 주는 얇은 조회 창구.
 *
 * <p>JWT principal 의 username 은 loginId(스태프) 또는 email(회원)이다({@code Member.authenticationName()}).
 * 신규 컨텍스트는 회원 UUID 로 저장하므로 여기서 한 번 바꿔 준다. 엔티티는 밖으로 내보내지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberDirectory {

    /** 광고·일반 회원 대상. DB 레거시 값 USER 는 MEMBER 와 같다({@link MemberRole#fromDb}). */
    public static final List<String> NON_STAFF_DB_ROLES = List.of("MEMBER", "EXPERT", "USER");

    private final MemberSpringDataRepository memberRepository;
    private final com.meditationmap.identity.infrastructure.jpa.MemberProfileSpringDataRepository profileRepository;

    /** principal username → 회원 UUID. 없으면 404 MEMBER_NOT_FOUND. */
    public String requireMemberId(String username) {
        return findByUsername(username)
                .map(MemberJpaEntity::getId)
                .orElseThrow(() -> new DomainArgumentException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public Optional<MemberBrief> findBrief(String memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return memberRepository.findById(memberId).map(MemberDirectory::toBrief);
    }

    public Map<String, MemberBrief> findBriefs(Collection<String> memberIds) {
        Map<String, MemberBrief> out = new HashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return out;
        }
        Set<String> unique = new LinkedHashSet<>(memberIds);
        unique.remove(null);
        for (MemberJpaEntity m : memberRepository.findAllById(unique)) {
            out.put(m.getId(), toBrief(m));
        }
        return out;
    }

    /** 지정 역할 스태프의 UUID(채팅 신고 알림 = ADMIN·EMPLOYEE, DC3). */
    public List<String> memberIdsWithRoles(MemberRole... roles) {
        List<String> ids = new ArrayList<>();
        for (MemberRole role : roles) {
            ids.addAll(memberRepository.findIdsByRole(role));
        }
        return ids;
    }

    /**
     * 고객 표시 이름(DC4 — 오너에게는 이름만). member_profiles.display_name, 비면 "회원 " + UUID 앞 4자 대문자.
     */
    public Map<String, String> displayNames(Collection<String> memberIds) {
        Map<String, String> out = new HashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return out;
        }
        Set<String> unique = new LinkedHashSet<>(memberIds);
        unique.remove(null);
        Map<String, String> names = new HashMap<>();
        for (var p : profileRepository.findAllById(unique)) {
            if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
                names.put(p.getMemberId(), p.getDisplayName().trim());
            }
        }
        for (String id : unique) {
            out.put(id, names.getOrDefault(id, fallbackName(id)));
        }
        return out;
    }

    public static String fallbackName(String memberId) {
        String head = memberId == null ? "" : memberId.replace("-", "");
        return "회원 " + head.substring(0, Math.min(4, head.length())).toUpperCase(Locale.ROOT);
    }

    /** 스태프(ADMIN·DEV·EMPLOYEE) 전원의 UUID. */
    public List<String> staffMemberIds() {
        List<String> ids = new ArrayList<>();
        for (MemberRole role : List.of(MemberRole.ADMIN, MemberRole.DEV, MemberRole.EMPLOYEE)) {
            ids.addAll(memberRepository.findIdsByRole(role));
        }
        return ids;
    }

    /**
     * 이메일 목록을 "가입한 비스태프 회원" UUID 로 바꾼다. 가입되지 않았거나 스태프인 이메일은 {@code unknownEmails}
     * 로 돌려준다(입력 순서·원문 유지, 중복 제거).
     */
    public EmailResolution resolveNonStaffByEmails(Collection<String> emails) {
        List<String> memberIds = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : emails) {
            if (raw == null) {
                continue;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                continue;
            }
            Optional<MemberJpaEntity> member = memberRepository.findByEmail(normalized);
            if (member.isPresent() && !member.get().getRole().isStaff()) {
                memberIds.add(member.get().getId());
            } else {
                unknown.add(raw.trim());
            }
        }
        return new EmailResolution(memberIds, unknown);
    }

    private Optional<MemberJpaEntity> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return memberRepository
                .findByLoginId(username)
                .or(() -> memberRepository.findByEmail(username));
    }

    private static MemberBrief toBrief(MemberJpaEntity m) {
        return new MemberBrief(m.getId(), m.getEmail(), m.getLoginId(), m.getRole());
    }

    public record MemberBrief(String memberId, String email, String loginId, MemberRole role) {}

    public record EmailResolution(List<String> memberIds, List<String> unknownEmails) {}
}
