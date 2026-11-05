package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;


    public void updateRole(UUID userId, Role newRole) {
        // 다음 단계에서 구현
    }

    public void updateLocked(UUID userId,boolean locked) {
        // 다음 단계에서 구현
    }
}
