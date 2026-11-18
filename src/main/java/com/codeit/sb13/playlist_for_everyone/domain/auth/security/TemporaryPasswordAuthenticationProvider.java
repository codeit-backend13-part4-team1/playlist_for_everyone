package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

/**
 * 비밀번호 초기화로 발급한 임시 비밀번호로 인증한다.
 *
 * 원래 비밀번호를 확인하는 DaoAuthenticationProvider가 실패했을 때 ProviderManager가 이어서 호출한다.
 * 파기되지 않았고 만료 전인 임시 비밀번호와 일치할 때만 성공한다.
 * 실패 사유는 구분하지 않고 BadCredentialsException으로 응답한다.
 */
@RequiredArgsConstructor
public class TemporaryPasswordAuthenticationProvider implements AuthenticationProvider {

    static final String BAD_CREDENTIALS_MESSAGE = "유효하지 않은 인증 정보입니다.";

    private final CustomUserDetailsService userDetailsService;
    private final TemporaryPasswordRepository temporaryPasswordRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Object credentials = authentication.getCredentials();
        if (credentials == null) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        String rawPassword = credentials.toString();

        SecurityUser user = loadUser(authentication.getName());

        LocalDateTime now = LocalDateTime.now();
        boolean matches = temporaryPasswordRepository
                .findAllByUserIdAndStatus(user.getUserId(), TemporaryPasswordStatus.ACTIVE)
                .stream()
                .filter(temporaryPassword -> temporaryPassword.isUsableAt(now))
                .anyMatch(temporaryPassword -> passwordEncoder.matches(rawPassword, temporaryPassword.getPassword()));

        if (!matches) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }

        // 임시 비밀번호가 맞아도 잠긴 계정은 로그인할 수 없다.
        if (!user.isAccountNonLocked()) {
            throw new LockedException("잠긴 계정입니다.");
        }

        // 원래 비밀번호 로그인과 같은 형태로 반환해야 성공 핸들러가 그대로 동작한다.
        UsernamePasswordAuthenticationToken result =
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        result.setDetails(authentication.getDetails());
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        // 이메일, 비밀번호 로그인 요청만 처리한다.
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    // 가입 여부가 드러나지 않도록 사용자 없음도 같은 인증 실패로 바꾼다.
    private SecurityUser loadUser(String email) {
        try {
            return userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException exception) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
    }

}
