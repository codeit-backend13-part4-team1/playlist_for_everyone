package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 이메일, 비밀번호 인증을 수행할 AuthenticationManager 설정
 */
@Configuration
public class SecurityAuthenticationConfig {

    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService customUserDetailsService,
                                                       PasswordEncoder passwordEncoder) {

        // 사용자 조회와 계정 상태, 비밀번호 검증을 수행하는 Provider
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);

        // 회원가입 때 사용한 BCrypt로 지정된 비밀번호를 검증합니다.
        provider.setPasswordEncoder(passwordEncoder);

        // 이메일 미존재와 비밀번호 불일치를 같은 인증 실패 예외로 처리한다.
        provider.setHideUserNotFoundExceptions(true);

        // 인증 요청을 처리할 Provider를 Manager에 등록한다.
        ProviderManager manager = new ProviderManager(provider);

        // 인증 성공 후 결과 객체에 비밀번호가 남지 않도록 제거한다.
        manager.setEraseCredentialsAfterAuthentication(true);

        return manager;
    }
}
