package com.meditationmap.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meditationmap.expert.infrastructure.jpa.ExpertJpaEntity;
import com.meditationmap.expert.infrastructure.jpa.ExpertSpringDataRepository;
import com.meditationmap.identity.application.port.out.PasswordHasher;
import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.Member;
import com.meditationmap.identity.domain.MemberId;
import com.meditationmap.identity.domain.MemberRepository;
import com.meditationmap.shared.exception.ErrorCode;
import com.meditationmap.shared.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 명상 전문가 계정을 대신 생성한다. 회원가입과 동일하게 아이디(loginId)·이메일·비밀번호로
 * EXPERT 역할 계정을 만들고, 전문가 프로필(JSON)을 그 계정에 연결(owner_member_id)한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminExpertAccountService {

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;
    private final ExpertSpringDataRepository expertRepo;

    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return false;
        }
        return !memberRepository.existsByLoginId(loginId.trim());
    }

    @CacheEvict(value = {"experts", "expert"}, allEntries = true)
    public ExpertJpaEntity createExpertAccount(
            String loginId, String email, String rawPassword, JsonNode data) {
        String trimmedLoginId = loginId == null ? "" : loginId.trim();
        Email emailVo = Email.of(email);
        if (data == null || !data.isObject()) {
            throw new InfrastructureException(ErrorCode.INVALID_REQUEST_BODY);
        }
        if (memberRepository.existsByLoginId(trimmedLoginId)) {
            throw new InfrastructureException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        if (memberRepository.existsByEmail(emailVo)) {
            throw new InfrastructureException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        MemberId memberId = MemberId.random();
        Member expertAccount =
                Member.registerExpertAccount(
                        memberId, trimmedLoginId, emailVo, passwordHasher.hash(rawPassword));
        memberRepository.save(expertAccount);

        String expertId =
                AdminNumericIdGenerator.nextId(
                        expertRepo.findAll().stream().map(ExpertJpaEntity::getId).toList());
        ObjectNode merged = (ObjectNode) data.deepCopy();
        merged.put("id", expertId);

        ExpertJpaEntity entity = new ExpertJpaEntity();
        entity.setId(expertId);
        entity.setOwnerMemberId(memberId.value());
        entity.setData(merged);
        return expertRepo.save(entity);
    }
}
