package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;


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
}
