package com.codeit.sb13.playlist_for_everyone.domain.user.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verifyNoInteractions;


@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 응답 변환")
class UserDtoMapperTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 30);

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private UserDtoMapper userDtoMapper;

    @Test
    @DisplayName("사용자 정보를 응답으로 변환한다.")
    void 사용자_정보를_응답으로_변환한다() {
        User user = createUser();
        user.lock();

        UserDto result = userDtoMapper.toDto(user);

        assertThat(result).isEqualTo(new UserDto(
                USER_ID, CREATED_AT, "a@test.com", "alice", null, Role.ADMIN, true
        ));
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("프로필 이미지가 있으면 파일 key로 URL을 만든다.")
    void 프로필_이미지가_있으면_파일_key로_URL을_만든다() {
        User user = createUser();
        user.updateProfileImage(FileInfo.createS3File("profile/a.png", "a.png", "image/png", 10L));
        given(fileStorage.getFileUrl("profile/a.png")).willReturn("https://cdn.test/profile/a.png");

        UserDto result = userDtoMapper.toDto(user);

        assertThat(result.profileImageUrl()).isEqualTo("https://cdn.test/profile/a.png");
    }


    private User createUser() {
        User user = User.builder()
                .email("a@test.com")
                .name("alice")
                .role(Role.ADMIN)
                .build();

        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "createdAt", CREATED_AT);
        return user;
    }

}
