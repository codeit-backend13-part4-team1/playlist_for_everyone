package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final UserService userService;
    private final JwtProperties jwtProperties;


    /**
     * 기존 Refresh Token을 검증하고 새 토큰 쌍으로 교체한다.
     */
    public TokenReissueResult reissue(String refreshToken) {
        // RED 단계: 테스트로 동작을 정의한 후 구현한다.
        throw new UnsupportedOperationException("아직 구현하지 않았습니다.");
    }




}
