package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 저장")
class FollowSaverTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FollowSaver followSaver;

    @Test
    @DisplayName("신규 팔로우를 저장하면 정확한 사용자 정보로 생성 이벤트를 한번 발행한다.")
    void save_publishesUserFollowedEvent(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        User follower = createUser(followerId, "팔로워");
        User followee = createUser(followeeId, "팔로우 대상");

        Follow follow = new Follow(follower, followee);
        ReflectionTestUtils.setField(follow, "id", UUID.randomUUID());
        given(followRepository.save(follow)).willReturn(follow);

        followSaver.save(follow);
        verify(followRepository).save(follow);

        ArgumentCaptor<UserFollowedEvent> captor = ArgumentCaptor.forClass(UserFollowedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UserFollowedEvent(followerId, "팔로워", followeeId));
    }

    private User createUser(UUID id, String name){
        User user = User.builder()
                .email(id + "@test.com")
                .name(name)
                .password("encoded-password")
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

}