package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.CustomUserDetailsService;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.TemporaryPasswordAuthenticationProvider;
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
                                                       PasswordEncoder passwordEncoder,
                                                       TemporaryPasswordRepository temporaryPasswordRepository) {

        // 사용자 조회와 계정 상태, 비밀번호 검증을 수행하는 Provider
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);

        // 원래 비밀번호가 맞지 않을 때 임시 비밀번호로 인증한다.
        // 잠긴 계정은 앞 Provider가 LockedException으로 바로 끝내므로 여기까지 오지 않는다.
        TemporaryPasswordAuthenticationProvider temporaryPasswordProvider =
                new TemporaryPasswordAuthenticationProvider(
                        customUserDetailsService, temporaryPasswordRepository, passwordEncoder);


        // 인증 요청을 처리할 Provider를 Manager에 등록한다.
        ProviderManager manager = new ProviderManager(provider, temporaryPasswordProvider);

        // 인증 성공 후 결과 객체에 비밀번호가 남지 않도록 제거한다.
        manager.setEraseCredentialsAfterAuthentication(true);

        return manager;
    }
}
