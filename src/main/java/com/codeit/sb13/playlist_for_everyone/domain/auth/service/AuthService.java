package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final UserService userService;
    private final JwtProperties jwtProperties;


    /**
     *  기존 Refresh Token을 검증하고 새 Access Token과 Refresh Token을 발급한다.
     *
     *  Redis의 원자적 교체에 성공한 경우에만 결과를 반환한다.
     */
    public TokenReissueResult reissue(String refreshToken) {

        UUID userId = parseRefreshToken(refreshToken);

        UUID storedUserId = refreshTokenRepository.findUserId(refreshToken)
                .orElseThrow(() -> new BadCredentialsException(
                        "유효하지 않은 인증 정보 입니다."
                ));

        if (!userId.equals(storedUserId)) {
            throw new BadCredentialsException("유효하지 않은 인증 정보입니다.");
        }

        User user = findUserForReissue(userId);

        if (user.isLocked()) {
            throw new BadCredentialsException(
                    "유효하지 않은 인증 정보 입니다."
            );
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        UserDto userDto = new UserDto(
                user.getId(),
                user.getCreatedAt(),
                user.getEmail(),
                user.getName(),
                null, // 프로필 이미지 연동 전에는 null처리
                user.getRole(),
                user.isLocked()
        );

        TokenReissueResult result = new TokenReissueResult(
                new JwtDto(userDto, newAccessToken),
                newRefreshToken
        );

        boolean rotated = refreshTokenRepository.rotate(
                userId,
                refreshToken,
                newRefreshToken,
                jwtProperties.refreshTokenValidity()
        );

        if (!rotated) {
            throw new BadCredentialsException("유효하지 않은 인증 정보 입니다.");
        }

        return result;
    }

    private UUID parseRefreshToken(String refreshToken) {
        try {
            return jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (JwtException exception) {
            // 만료·변조·잘못된 토큰 종류 등을 동일한 인증 실패로 처리한다.
            throw new BadCredentialsException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }

    /**
     * 사용자 미존재를 일반 사용자 조회의 404가 아닌 인증 실패로 처리한다.
     */
    private User findUserForReissue(UUID userId) {
        try {
            return userService.getById(userId);
        } catch (UserNotFoundException exception) {
            throw new BadCredentialsException(
                    "유효하지 않은 인증 정보 입니다.",
                    exception
            );
        }
    }

    /**
     * 전달받은 Refresh Token의 등록 정보를 삭제한다.
     */
    public void logout(String refreshToken) {
        // RED : 다음 단계에서 Redis 삭제를 구현한다.
        throw new UnsupportedOperationException("로그아웃 구현 예정");
    }




}
