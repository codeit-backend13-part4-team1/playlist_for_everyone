package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 이메일로 사용자를 조회하고 Spring Security 인증 정보로 변환한다.
 * 비밀번호 비교와 계정 잠금 상태 검증은 인증 Provider가 담당한다
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public SecurityUser loadUserByUsername(String username) {
        // 로그인 요청의 username 필드에는 이메일이 전달된다.
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 인증 정보를 찾을 수 없습니다."));

        // DB 엔티티를 인증에 필요한 정보만 가진 객체로 변환한다.
        return new SecurityUser(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole(),
                user.isLocked()
        );
    }
}
