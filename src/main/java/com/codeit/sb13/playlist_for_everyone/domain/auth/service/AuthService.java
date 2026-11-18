package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.RefreshTokenClaims;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
     *  현재 로그인 세션의 토큰일 때만 재발급하며, 재발급해도 세션 ID는 유지한다.
     *  Redis의 원자적 교체에 성공한 경우에만 결과를 반환한다.
     */
    public TokenReissueResult reissue(String refreshToken) {

        RefreshTokenClaims claims = parseRefreshToken(refreshToken);

        UUID userId = claims.userId();
        UUID sessionId = claims.sessionId();

        // 다른 곳에서 다시 로그인했거나 강제 로그아웃된 세션이면,
        // Refresh Token이 Redis에 남아 있어도 재발급하지 않는다.
        if (!userSessionRepository.isCurrent(userId, sessionId)) {
            throw new BadCredentialsException("유효하지 않은 인증 정보입니다.");
        }

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

        // 같은 로그인 세션을 유지한 채 토큰만 새로 발급한다.
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, sessionId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId, sessionId);


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

        boolean extended;
        try {
            extended = userSessionRepository.extendIfCurrent(
                    userId,
                    sessionId,
                    jwtProperties.refreshTokenValidity()
            );
        } catch (DataAccessException exception) {
            // 기존 Refresh Token은 이미 교체되었으므로, 여기서 실패하면 다시 로그인해야 한다.
            // 연장하지 못해도 세션은 남은 유효기간 동안 유지되므로 새 토큰을 반환한다.
            log.warn("로그인 세션 연장에 실패했습니다. userId={}", userId, exception);
            return result;
        }

        if (!extended) {
            throw new BadCredentialsException("유효하지 않은 인증 정보입니다.");
        }

        return result;
    }

    private RefreshTokenClaims parseRefreshToken(String refreshToken) {
        try {
            return jwtTokenProvider.parseRefresh(refreshToken);
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
     * 로그아웃 요청으로 전달받은 Refresh Token의 로그인 세션을 종료하고 등록 정보를 삭제한다.
     *
     * 세션을 종료하면 이 로그인에서 발급한 Access Token도 바로 사용할 수 없다.
     * 다른 곳에서 이미 새로 로그인했다면 그 세션은 유지한다.
     *
     * 삭제된 토큰은 Redis 등록 정보가 없으므로 이후 재발급에 사용할 수 없다.
     * 이미 등록 정보가 없는 토큰의 삭제는 저장소 계약에 따라 정상 처리한다.
     */
    public void logout(String refreshToken) {
        // 만료·변조된 토큰이면 세션을 알 수 없으므로 세션 종료만 생략한다.
        // 로그아웃 자체는 실패시키지 않고 등록 정보 삭제를 진행한다.
        try {
            RefreshTokenClaims claims = jwtTokenProvider.parseRefresh(refreshToken);
            userSessionRepository.endIfCurrent(claims.userId(), claims.sessionId());
        } catch (JwtException exception) {
            log.debug("로그아웃 토큰에서 로그인 세션을 확인하지 못했습니다. exceptionType={}",
                    exception.getClass().getSimpleName());
        }

        // Redis 삭제 실패는 호출자에게 전달한다.
        // 삭제에 실패 했는데 로그아웃에 성공한 것처럼 처리하지 않는다.
        refreshTokenRepository.delete(refreshToken);
    }




}
