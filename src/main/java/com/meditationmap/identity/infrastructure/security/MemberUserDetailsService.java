package com.meditationmap.identity.infrastructure.security;

import com.meditationmap.identity.domain.Email;
import com.meditationmap.identity.domain.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var member =
                memberRepository
                        .findByLoginId(username)
                        .or(() -> findByEmailSafe(username))
                        .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.builder()
                .username(member.authenticationName())
                .password(member.getPasswordHash())
                .roles(member.getRole().name())
                .build();
    }

    private Optional<com.meditationmap.identity.domain.Member> findByEmailSafe(String username) {
        try {
            return memberRepository.findByEmail(Email.of(username));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
