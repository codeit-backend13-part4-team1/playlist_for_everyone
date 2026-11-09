package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
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
    private final UserSessionRepository  userSessionRepository;


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

        UserDto userDto = findUserForReissue(userId);

        if (userDto.locked()) {
            throw new BadCredentialsException(
                    "유효하지 않은 인증 정보 입니다."
            );
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);


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
    private UserDto findUserForReissue(UUID userId) {
        try {
            return userService.getUserDtoById(userId);
        } catch (UserNotFoundException exception) {
            throw new BadCredentialsException(
                    "유효하지 않은 인증 정보 입니다.",
                    exception
            );
        }
    }

    /**
     * 로그아웃 요청으로 전달받은 Refresh Token의 등록 정보를 삭제한다.
     *
     * 삭제된 토큰은 Redis 등록 정보가 없으므로 이후 재발그에 사용할 수 없다.
     * 이미 등록 정보가 없는 토큰의 삭제는 저장소 계약에 따라 정상 처리한다.
     */
    public void logout(String refreshToken) {
        // Redis 삭제 실패는 호출자에게 전달한다.
        // 삭제에 실패 했는데 로그아웃에 성공한 것처럼 처리하지 않는다.
        refreshTokenRepository.delete(refreshToken);
    }




}
