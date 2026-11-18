package com.codeit.sb13.playlist_for_everyone.domain.user.entity;

import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("사용자를 생성하면 프로필 이미지가 없는 상태다")
class UserProfileImageTest {


    @Test
    @DisplayName("사용자를 생성하면 프로필 이미지가 없는 상태다.")
    void 생성하면_프로필_이미지가_없다() {
        User user = createUser();

        // 생성 당시에는 프로필 이미지가 없다.
        assertThat(user.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("프로필 이미지를 생성할 수 있다.")
    void 프로필_이미지를_설정한다() {
        User user = createUser();
        FileInfo image = createImage("profiles/first.png");

        user.updateProfileImage(image);
        assertThat(user.getProfileImage()).isSameAs(image);
    }

    @Test
    @DisplayName("기존 프로필 이미지를 새 이미지로 교체할 수 있다.")
    void 프로필_이미지를_교체한다() {
        User user = createUser();
        FileInfo oldImage = createImage("profiles/old.png");
        FileInfo newImage = createImage("profiles/new.png");

        user.updateProfileImage(oldImage);

        user.updateProfileImage(newImage);

        assertThat(user.getProfileImage()).isSameAs(newImage);
    }

    @Test
    @DisplayName("프로필 이미지 연결을 해제할 수 있다.")
    void 프로필_이미지_연결을_해제한다() {
        User user = createUser();
        user.updateProfileImage(createImage("profiles/image.png"));

        user.removeProfileImage();

        assertThat(user.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("프로필 이미지 변경에 null을 전달하면 거절하고 기존 연결을 유지한다.")
    void null로_변경하면_기존_연결을_유지한다() {
        User user = createUser();
        FileInfo image = createImage("profiles/image.png");
        user.updateProfileImage(image);

        assertThatThrownBy(()-> user.updateProfileImage(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("프로필 이미지 정보는 필수입니다.");

        assertThat(user.getProfileImage()).isSameAs(image);
    }

    private User createUser() {
        return User.builder()
                .email("test@gmail.com")
                .name("사용자")
                .password("encoded-password")
                .role(Role.USER)
                .build();
    }

    private FileInfo createImage(String key) {
        return FileInfo.createS3File(
                key,
                "profile.png",
                "image/png",
                1024L
        );
    }
}
