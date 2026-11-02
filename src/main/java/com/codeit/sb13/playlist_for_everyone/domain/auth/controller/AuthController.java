package com.codeit.sb13.playlist_for_everyone.domain.auth.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {


    private final AuthService authService;


    /**
     * 쿠키에 담긴 Refresh Token으로 새 토큰 쌍을 발급한다.
     *
     * 쿠키 누락도 인증 실패인 401로 처리하기 위해
     * required=false로 받고 직접 검증할 예정
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtDto> reissue(@CookieValue(name = "REFRESH_TOKEN", required = false) String refreshToken) {
        // RED 단계: 다음 GREEN 단계에서 서비스와 응답을 연결한다.
        throw new UnsupportedOperationException("아직 구현하지 않았습니다");
    }

}
