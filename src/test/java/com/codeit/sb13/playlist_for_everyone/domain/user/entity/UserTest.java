package com.codeit.sb13.playlist_for_everyone.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User 엔티티")
class UserTest {


    private User createUser() {
        return User.builder()
                .email("test@email.com")
                .name("테스트")
                .password("encoded-password")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("생성하면 활성 상태이고 전달한 권한을 가진다")
    void 생성하면_활성_상태를가지고_전달한_권한을_가진다() {
        User user = createUser();

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    @DisplayName("이름을 변경할 수 있다")
    void 이름을_변경할_수_있다() {
        User user = createUser();

        user.updateName("테스트2");

        assertThat(user.getName()).isEqualTo("테스트2");
    }

    @Test
    @DisplayName("이름에 null을 주면 기존 이름을 유지한다")
    void 이름에_null을_주면_기존_이름을_유지한다() {
        User user = createUser();

        user.updateName(null);

        assertThat(user.getName()).isEqualTo("테스트");
    }

    @ParameterizedTest(name = "{index}: 입력값: \"{0}\"")
    @ValueSource(strings = {"", " ", "   ", "\t", "\n", " \t\n "})
    @DisplayName("이름이 빈 문자열이거나 공백뿐이면 기존 이름을 유지한다.")
    void 이름이_비어있으면_기존_이름을_유지한다(String name){
        User user = createUser();

        user.updateName(name);

        assertThat(user.getName()).isEqualTo("테스트");
    }


    @Test
    @DisplayName("권한을 변경할 수 있다")
    void 유저_권한_변경() {
        User user = createUser();

        user.updateRole(Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("생성하면 잠기지 않은 상태이다")
    void 생성하면_잠기지_않은_상태이다() {

        User user = createUser();

        assertThat(user.isLocked()).isFalse();
    }

    @Test
    @DisplayName("잠그면 잠긴 상태가 된다")
    void 잠그면_잠긴_상태가_된다() {

        User user = createUser();

        // 유저 잠금
        user.lock();

        assertThat(user.isLocked()).isTrue();
    }

    @Test
    @DisplayName("잠긴 계정을 해제하면 활성 상태가 된다.")
    void 잠긴_계정을_해제하면_활성_상태가_된다() {
        User user = createUser();
        user.lock();

        user.unlock();

        assertThat(user.isLocked()).isFalse();
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("권한 없이 유저를 생성하면 예외가 발생한다")
    void 권한_없이_유저를_생성하면_예외가_발생한다() {

        assertThatThrownBy(() -> User.builder()
                .email("test@gmail.com")
                .name("test")
                .password("encoded-password")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("role은 필수입니다.");
    }



}