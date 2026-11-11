package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.UserSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserLockedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserDtoMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserDtoMapper userDtoMapper;


    @Transactional
    public void updateRole(UUID userId, Role newRole) {
        Objects.requireNonNull(newRole, "변경할 권한은 필수입니다.");

        User user = findUserForUpdate(userId);
        Role previousRole = user.getRole();

        if (previousRole == newRole) {
            return;
        }
        user.updateRole(newRole);

        eventPublisher.publishEvent(
                new UserRoleChangedEvent(userId, previousRole, newRole)
        );

    }

    @Transactional
    public void updateLocked(UUID userId,boolean locked) {
        User user = findUserForUpdate(userId);

        if (user.isLocked() == locked) {
            return;
        }

        if (locked) {
            user.lock();

            // 잠금이 커밋되면 인증 도메인이 해당 사용자의 로그인 세션을 종료한다.
            // 잠금 해제는 로그인 세션을 종료할 이유가 없으므로 발행하지 않는다.
            eventPublisher.publishEvent(new UserLockedEvent(userId));
        } else {
            user.unlock();
        }
    }

    private User findUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        Map.of("userId", userId)
                ));
    }

    /**
     * 관리자 사용자 목록 조회
     * limit + 1개를 조회해 다음 페이지 여부를 판단하고, 마지막 항목으로 다음 커서를 만든다.
     */
    @Transactional(readOnly = true)
    public CursorResponse<UserDto> getUsers(UserSearchCondition condition) {
        List<User> fetched = userRepository.search(condition);
        long totalCount = userRepository.count(condition);

        boolean hasNext = fetched.size() > condition.limit();
        List<User> page = hasNext ? fetched.subList(0, condition.limit()) : fetched;

        List<UserDto> data = page.stream()
                .map(userDtoMapper::toDto)
                .toList();

        String nextCursor = null;
        UUID nextIdAfter = null;
        if (hasNext) {
            User last = page.get(page.size() - 1);
            nextCursor = toCursor(last, condition.sortBy());
            nextIdAfter = last.getId();
        }

        return new CursorResponse<>(
                data, nextCursor, nextIdAfter, hasNext, totalCount,
                condition.sortBy().getSortKey(), condition.sortDirection()
        );

    }

    /**
     * 다음 페이지 요청에 사용할 커서 값
     * 레포지토리가 비교하는 정렬 키와 같은 값이어야 한다.
     */
    private String toCursor(User user, UserSortBy sortBy) {
        return switch (sortBy) {
            case NAME -> user.getName() == null ? "" : user.getName();
            case EMAIL -> user.getEmail();
            case CREATED_AT -> user.getCreatedAt().toString();
            case IS_LOCKED -> String.valueOf(user.isLocked());
            case ROLE -> user.getRole().name();
        };
    }

}
