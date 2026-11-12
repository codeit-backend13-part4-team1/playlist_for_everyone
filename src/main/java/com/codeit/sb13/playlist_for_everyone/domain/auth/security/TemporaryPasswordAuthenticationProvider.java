package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

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
        // RED: 다음 단계에서 구현
        throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        // RED: 다음 단계에서 구현
        return false;
    }
}
