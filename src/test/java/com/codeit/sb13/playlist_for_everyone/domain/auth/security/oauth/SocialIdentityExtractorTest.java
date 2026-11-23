package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("소셜 로그인 결과 변환")
class SocialIdentityExtractorTest {

    private final SocialIdentityExtractor extractor = new SocialIdentityExtractor();

    @Test
    @DisplayName("Google ID 토큰에서 회원 ID, 이메일, 이름, 이메일 확인 여부를 읽는다.")
    void Google_ID_토큰에서_회원ID_이메일_이름_이메일_확인_여부를_읽는다() {
        Authentication authentication = google(Map.of(
                "sub", "google-sub",
                "email", "test@gmail.com",
                "email_verified", true,
                "name", "사용자"
        ));

        SocialIdentity identity = extractor.extract(authentication);

        assertThat(identity.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(identity.providerUserId()).isEqualTo("google-sub");
        assertThat(identity.email()).isEqualTo("test@gmail.com");
        assertThat(identity.name()).isEqualTo("사용자");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("Google 이메일 확인 여부가 없으면 확인되지 않은 것으로 본다.")
    void Google_이메일_확인_여부가_없으면_확인되지_않은_것으로_본다() {
        Authentication authentication = google(Map.of(
                "sub", "google-sub",
                "email", "test@gmail.com"
        ));

        assertThat(extractor.extract(authentication).emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Kakao 회원 ID와 프로필 닉네임을 읽고 이메일은 비워 둔다.")
    void Kakao_회원ID와_프로필_닉네임을_읽고_이메일은_비워_둔다() {
        Authentication authentication = kakao(Map.of(
                "id", 38457L,
                "kakao_account", Map.of("profile", Map.of("nickname", "카카오"))
        ));

        SocialIdentity identity = extractor.extract(authentication);

        assertThat(identity.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(identity.providerUserId()).isEqualTo("38457");
        assertThat(identity.name()).isEqualTo("카카오");
        assertThat(identity.email()).isNull();
        assertThat(identity.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Kakao 닉네임 정보가 없으면 이름 없이 만든다.")
    void Kakao_닉네임_정보가_없으면_이름_없이_만든다() {
        Authentication authentication = kakao(Map.of("id", 38457L));

        SocialIdentity identity = extractor.extract(authentication);

        assertThat(identity.providerUserId()).isEqualTo("38457");
        assertThat(identity.name()).isNull();
    }

    @Test
    @DisplayName("사용 중단된 properties.nickname은 읽지 않는다.")
    void 사용_중단된_properties_nickname은_읽지_않는다() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 38457L);
        attributes.put("properties", Map.of("nickname", "옛날 닉네임"));

        assertThat(extractor.extract(kakao(attributes)).name()).isNull();
    }

    @Test
    @DisplayName("지원하지 않는 제공자면 로그인 실패로 처리한다.")
    void 지원하지_않는_제공자면_로그인_실패로_처리한다() {
        OAuth2User user = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("OAUTH2_USER"), Map.of("id", "n-1"), "id");
        Authentication authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "naver");

        assertLoginFailed(authentication);
    }

    @Test
    @DisplayName("OAuth 로그인 결과가 아니면 로그인 실패로 처리한다.")
    void OAuth_로그인_결과가_아니면_로그인_실패로_처리한다() {
        assertLoginFailed(UsernamePasswordAuthenticationToken.authenticated("user", null, null));
    }

    private Authentication google(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken(
                "id-token", Instant.now(), Instant.now().plusSeconds(60), claims
        );
        DefaultOidcUser user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }

    private Authentication kakao(Map<String, Object> attributes) {
        OAuth2User user = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("OAUTH2_USER"), attributes, "id"
        );
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "kakao");
    }

    private void assertLoginFailed(Authentication authentication) {
        assertThatThrownBy(() -> extractor.extract(authentication))
                .isInstanceOf(SocialLoginException.class)
                .extracting(e -> ((SocialLoginException) e).getError())
                .isEqualTo(SocialLoginError.LOGIN_FAILED);
    }

}