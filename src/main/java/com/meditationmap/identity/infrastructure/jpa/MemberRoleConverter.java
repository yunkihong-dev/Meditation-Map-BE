package com.meditationmap.identity.infrastructure.jpa;

import com.meditationmap.identity.domain.MemberRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DB legacy 값 USER → MEMBER 매핑 */
@Converter(autoApply = true)
public class MemberRoleConverter implements AttributeConverter<MemberRole, String> {

    @Override
    public String convertToDatabaseColumn(MemberRole role) {
        return role == null ? MemberRole.MEMBER.name() : role.name();
    }

    @Override
    public MemberRole convertToEntityAttribute(String db) {
        return MemberRole.fromDb(db);
    }
}
