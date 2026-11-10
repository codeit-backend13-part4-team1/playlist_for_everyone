package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.UserSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserDtoMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 사용자 목록 조회")
class UserAdministrationServiceGetUsersTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserDtoMapper userDtoMapper;

    @InjectMocks
    private UserAdministrationService userAdministrationService;

    private final User alice = createUser("a@test.com", "alice", Role.USER, false,
            LocalDateTime.of(2026, 10, 1, 9, 30));
    private final User bob = createUser("b@test.com", "bob", Role.ADMIN, true,
            LocalDateTime.of(2026, 10, 2, 9, 30));
    private final User charlie = createUser("c@test.com", "charlie", Role.USER, false,
            LocalDateTime.of(2026, 10, 3, 9, 30));

    @Test
    @DisplayName("limit보다 많이 조회되면 limit개만 반환하고 다음 커서를 만든다.")
    void limit보다_많이_조회되면_limit개만_반환하고_다음_커서를_만든다() {
        UserSearchCondition condition = condition(UserSortBy.NAME, 2);
        given(userRepository.search(condition)).willReturn(List.of(alice, bob, charlie));
        given(userRepository.count(condition)).willReturn(3L);
        stubMapper();

        CursorResponse<UserDto> result = userAdministrationService.getUsers(condition);

        assertThat(result.data()).extracting(UserDto::id)
                .containsExactly(alice.getId(), bob.getId());
        assertThat(result.hasNext()).isTrue();
        // 다음 페이지는 이번 페이지의 마지막 사용자(bob) 다음부터 시작한다.
        assertThat(result.nextCursor()).isEqualTo("bob");
        assertThat(result.nextIdAfter()).isEqualTo(bob.getId());
    }

    @Test
    @DisplayName("마지막 페이지면 다음 커서가 없다.")
    void 마지막_페이지면_다음_커서가_없다() {
        UserSearchCondition condition = condition(UserSortBy.NAME, 2);
        given(userRepository.search(condition)).willReturn(List.of(alice, bob));
        given(userRepository.count(condition)).willReturn(2L);
        stubMapper();

        CursorResponse<UserDto> result = userAdministrationService.getUsers(condition);

        assertThat(result.data()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("전체 개수와 정렬 정보를 함께 반환한다.")
    void 전체_개수와_정렬_정보를_함께_반환한다() {
        UserSearchCondition condition = new UserSearchCondition(
                null, null, null, null, null,
                20, SortDirection.DESCENDING, UserSortBy.CREATED_AT);
        given(userRepository.search(condition)).willReturn(List.of());
        given(userRepository.count(condition)).willReturn(42L);

        CursorResponse<UserDto> result = userAdministrationService.getUsers(condition);

        assertThat(result.totalCount()).isEqualTo(42L);
        // 응답의 sortBy는 enum 이름이 아니라 API 값이다.
        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "NAME, bob",
            "EMAIL, b@test.com",
            "CREATED_AT, 2026-10-02T09:30",
            "IS_LOCKED, true",
            "ROLE, ADMIN"
    })
    @DisplayName("정렬 기준에 맞는 다음 커서를 만든다.")
    void 정렬_기준에_맞는_다음_커서를_만든다(UserSortBy sortBy, String expectedCursor) {
        UserSearchCondition condition = condition(sortBy, 2);
        given(userRepository.search(condition)).willReturn(List.of(alice, bob, charlie));
        given(userRepository.count(condition)).willReturn(3L);
        stubMapper();

        CursorResponse<UserDto> result = userAdministrationService.getUsers(condition);

        assertThat(result.nextCursor()).isEqualTo(expectedCursor);
    }

    // ---------- 도우미

    private UserSearchCondition condition(UserSortBy sortBy, int limit) {
        return new UserSearchCondition(
                null, null, null, null, null,
                limit, SortDirection.ASCENDING, sortBy);
    }

    // 변환 자체는 UserDtoMapperTest가 검증한다. 여기서는 받은 사용자를 그대로 옮긴다.
    private void stubMapper() {
        given(userDtoMapper.toDto(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new UserDto(user.getId(), user.getCreatedAt(), user.getEmail(),
                    user.getName(), null, user.getRole(), user.isLocked());
        });
    }

    private static User createUser(String email, String name, Role role, boolean locked,
                                   LocalDateTime createdAt) {
        User user = User.builder()
                .email(email)
                .name(name)
                .role(role)
                .build();
        if (locked) {
            user.lock();
        }
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        return user;
    }

}
