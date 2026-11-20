package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 제공자별 OAuth 로그인 결과를 공통 형태(SocialIdentity)로 바꾼다.\
 *
 * Google은 Spring Security가 검증한 ID 토큰의 claim을,
 * Kakao는 사용자 정보 API(/v2/user/me) 응답을 사용한다.
 */
@Component
public class SocialIdentityExtractor {

    private static final String GOOGLE = "google";
    private static final String KAKAO = "kakao";

    public SocialIdentity extract(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new SocialLoginException(SocialLoginError.LOGIN_FAILED);
        }

        return switch (token.getAuthorizedClientRegistrationId()) {
            case GOOGLE -> fromGoogle(token.getPrincipal());
            case KAKAO -> fromKakao(token.getPrincipal());
            default -> throw new SocialLoginException(SocialLoginError.LOGIN_FAILED);
        };
    }

    /**
     * Google은 이메일이 바뀔 수 있어 sub를 회원 ID로 사용한다.
     * 이메일 확인 여부가 없으면 확인되지 않은 것으로 본다.
     */
    private SocialIdentity fromGoogle(OAuth2User principal) {
        if (!(principal instanceof OidcUser oidcUser)) {
            throw new SocialLoginException(SocialLoginError.LOGIN_FAILED);
        }

        OidcIdToken idToken = oidcUser.getIdToken();
        return new SocialIdentity(
                SocialProvider.GOOGLE,
                idToken.getSubject(),
                idToken.getEmail(),
                idToken.getFullName(),
                Boolean.TRUE.equals(idToken.getEmailVerified())
        );
    }

    private SocialIdentity fromKakao(OAuth2User principal) {
        Object id = principal.getAttribute("id");
        if (id == null) {
            throw new SocialLoginException(SocialLoginError.LOGIN_FAILED);
        }

        return new SocialIdentity(SocialProvider.KAKAO, String.valueOf(id), null, kakaoNickname(principal), false);
    }

    private String kakaoNickname(OAuth2User principal) {
        if (principal.getAttribute("kakao_account") instanceof Map<?, ?> account
                && account.get("profile") instanceof Map<?, ?> profile
                && profile.get("nickname") instanceof String nickname) {
            return nickname;
        }
        return null;
    }

}
