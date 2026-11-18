package com.codeit.sb13.playlist_for_everyone.global.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;

/**
 * 콘텐츠와 DM이 공유하는 WebSocket·SockJS 연결의 추가 허용 Origin을 바인딩한다.
 *
 * <p>기본값은 dev 프로필에서만 제공한다. prod는 외부 설정이 필수이며,
 * 누락되거나 Origin 형식이 잘못되면 기동을 중단한다. 이 목록은 STOMP JWT 인증을 대체하지 않는다.
 */
@ConfigurationProperties(prefix = "everyone.websocket")
public record WebSocketOriginProperties(List<String> allowedOrigins) {

    public WebSocketOriginProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("WebSocket 허용 Origin은 한 개 이상 설정해야 합니다.");
        }
        for (String origin : allowedOrigins) {
            validateOrigin(origin);
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    private static void validateOrigin(String origin) {
        if (!StringUtils.hasText(origin)) {
            throw new IllegalArgumentException("WebSocket 허용 Origin은 null, 빈 문자열 또는 공백일 수 없습니다.");
        }

        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("WebSocket 허용 Origin의 주소 형식이 올바르지 않습니다.", exception);
        }

        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535
                || origin.contains("*")) {
            throw new IllegalArgumentException(
                    "WebSocket 허용 Origin은 경로·쿼리·사용자 정보·와일드카드 없이 http 또는 https 주소와 유효한 포트로 설정해야 합니다.");
        }
    }
}
