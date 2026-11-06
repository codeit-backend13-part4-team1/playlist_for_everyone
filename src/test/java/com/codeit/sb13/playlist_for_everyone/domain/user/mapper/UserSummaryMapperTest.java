package com.codeit.sb13.playlist_for_everyone.domain.user.mapper;


import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 요약 정보 변환")
class UserSummaryMapperTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String USER_NAME = "테스트 사용자";
    private static final String PROFILE_KEY = "profiles/test-user.png";

    // 실제 S3에 접근하지 않으며, 저장소가 반환하는 URL을 그대로 전달하는지 확인한다.
    private static final String PROFILE_URL =
            "https://storage.example.com/profiles/test-user.png?signature=test";


    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private UserSummaryMapper userSummaryMapper;

    @Test
    @DisplayName("프로필 이미지가 없으면 사용자 정보와 null URL을 반환한다")
    void 프로필_이미지가_없으면_URL은_null이다() {
        // given
        User user = createUser();

        // when
        UserSummary result = userSummaryMapper.toSummary(user);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.name()).isEqualTo(USER_NAME);
        assertThat(result.profileImageUrl()).isNull();

        // 이미지가 없는 사용자를 변환할 때는 저장소를 호출하지 않는다.
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("프로필 이미지가 있으면 파일 key로 생성한 URL을 반환한다")
    void 프로필_이미지가_있으면_생성된_URL을_반환한다() {
        // given
        User user = createUser();
        user.updateProfileImage(createProfileImage());

        given(fileStorage.getFileUrl(PROFILE_KEY))
                .willReturn(PROFILE_URL);

        // when
        UserSummary result = userSummaryMapper.toSummary(user);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.name()).isEqualTo(USER_NAME);
        assertThat(result.profileImageUrl()).isEqualTo(PROFILE_URL);

        // 파일 ID로 다시 조회하지 않고, 이미 조회된 파일의 key를 사용한다.
        verify(fileStorage).getFileUrl(PROFILE_KEY);
        verifyNoMoreInteractions(fileStorage);
    }

    @Test
    @DisplayName("URL 생성에 실패하면 예외를 숨기지 않고 전달한다")
    void URL_생성_실패는_그대로_전달한다() {
        // given
        User user = createUser();
        user.updateProfileImage(createProfileImage());

        IllegalStateException storageException =
                new IllegalStateException("테스트용 URL 생성 실패");

        given(fileStorage.getFileUrl(PROFILE_KEY))
                .willThrow(storageException);

        // when & then
        // 저장소 오류를 이미지 없음(null)으로 처리하면 장애 원인이 가려질 수 있다.
        assertThatThrownBy(() -> userSummaryMapper.toSummary(user))
                .isSameAs(storageException);

        verify(fileStorage).getFileUrl(PROFILE_KEY);
    }



    private User createUser() {
        User user = User.builder()
                .email("summary-test@example.com")
                .name(USER_NAME)
                .password("encoded-test-password")
                .role(Role.USER)
                .build();

        // DB 저장 없이 엔티티를 생성하므로 테스트에서 식별자를 지정한다.
        ReflectionTestUtils.setField(user, "id", USER_ID);

        return user;
    }




    private FileInfo createProfileImage() {
        // 현재 FileInfo에서 제공하는 팩터리 메서드를 그대로 사용한다.
        return FileInfo.createS3File(
                PROFILE_KEY,
                "test-user.png",
                "image/png",
                1024L
        );
    }
}