package com.codeit.sb13.playlist_for_everyone.domain.auth.entity;


import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("소셜 계정 연결 정보")
class SocialAccountTest {

    @Test
    @DisplayName("사용자와 제공자, 회원 ID로 소셜 계정을 만든다.")
    void 사용자와_제공자_회원ID로_소셜_계정을_만든다 () {
        User user = createUser();

        SocialAccount account = SocialAccount.connect(user, SocialProvider.GOOGLE, "google-sub-123");

        assertThat(account.getUser()).isSameAs(user);
        assertThat(account.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(account.getProviderUserId()).isEqualTo("google-sub-123");
    }

    @Test
    @DisplayName("사용자가 없으면 만들 수 없다.")
    void 사용자가_없으면_만들_수_없다() {
        assertThatThrownBy(()-> SocialAccount.connect(null, SocialProvider.KAKAO, "12345"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("제공자가 없으면 만들 수 없다.")
    void 제공자가_없으면_만들_수_없다() {
        assertThatThrownBy(() -> SocialAccount.connect(createUser(), null, "12345"))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "회원 ID=[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("제공자 회원 ID가 없거나 비어 있으면 만들 수 없다.")
    void 제공자_회원ID가_없거나_비어_있으면_만들_수_없다(String providerUserId) {
        assertThatThrownBy(() -> SocialAccount.connect(createUser(), SocialProvider.KAKAO, providerUserId))
                .isInstanceOf(IllegalArgumentException.class);
    }


    private User createUser() {
        return User.builder()
                .email("social@example.com")
                .name("소셜 사용자")
                .password(null)
                .role(Role.USER)
                .build();
    }

}