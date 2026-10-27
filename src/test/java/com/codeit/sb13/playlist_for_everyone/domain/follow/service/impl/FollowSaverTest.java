package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 저장")
class FollowSaverTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private FollowUserReader followUserReader;

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
        FollowCommand command = new FollowCommand(followeeId, followerId);
        ReflectionTestUtils.setField(follow, "id", UUID.randomUUID());

        given(followUserReader.getById(followerId)).willReturn(follower);
        given(followUserReader.getById(followeeId)).willReturn(followee);
        given(followRepository.save(any(Follow.class))).willReturn(follow);

        followSaver.save(command);
        ArgumentCaptor<Follow> followCaptor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(followCaptor.capture());

        Follow requestedFollow = followCaptor.getValue();
        assertThat(requestedFollow.getFollower()).isSameAs(follower);
        assertThat(requestedFollow.getFollowee()).isSameAs(followee);

        ArgumentCaptor<UserFollowedEvent> captor = ArgumentCaptor.forClass(UserFollowedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UserFollowedEvent(followerId, "팔로워", followeeId));
    }

    @Test
    @DisplayName("팔로우 저장 호출에서 예외가 발생하면 예외를 전달하고 생성 이벤트를 발행하지 않는다.")
    void save_doesNotPublishEventWhenRepositoryThrows(){
        User follower = createUser(UUID.randomUUID(), "팔로워");
        User followee = createUser(UUID.randomUUID(), "팔로우 대상");

        FollowCommand command = new FollowCommand(followee.getId(), follower.getId());

        DataIntegrityViolationException exception = new DataIntegrityViolationException("팔로우 저장 실패");

        given(followUserReader.getById(follower.getId())).willReturn(follower);
        given(followUserReader.getById(followee.getId())).willReturn(followee);
        given(followRepository.save(any(Follow.class))).willThrow(exception);

        assertThatThrownBy(()-> followSaver.save(command)).isSameAs(exception);

        verify(followRepository).save(any(Follow.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이벤트 발행 후 사용자 이름이 변경되어도 이벤트는 발행 당시 이름을 유지한다.")
    void save_preservesFollowerNameAtPublication(){
        User follower = createUser(UUID.randomUUID(), "변경 전 이름");
        User followee = createUser(UUID.randomUUID(), "팔로우 대상");
        Follow follow = new Follow(follower, followee);
        FollowCommand command = new FollowCommand(followee.getId(), follower.getId());

        ReflectionTestUtils.setField(follow,"id", UUID.randomUUID());

        given(followUserReader.getById(follower.getId())).willReturn(follower);
        given(followUserReader.getById(followee.getId())).willReturn(followee);
        given(followRepository.save(any(Follow.class))).willReturn(follow);
        followSaver.save(command);

        ArgumentCaptor<UserFollowedEvent> captor = ArgumentCaptor.forClass(UserFollowedEvent.class);

        verify(eventPublisher).publishEvent(captor.capture());

        UserFollowedEvent publishEvent = captor.getValue();

        follower.updateName("변경 후 이름");
        assertThat(follower.getName()).isEqualTo("변경 후 이름");
        assertThat(publishEvent.followerName()).isEqualTo("변경 전 이름");

    }

    @ParameterizedTest(name = "[{index}] 이름=[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("사용자 이름이 없거나 공백이어도 이벤트에 원본 그대로 전달한다.")
    void save_preservesNullOrBlankFollowerName(String followerName){
        User follower = createUser(UUID.randomUUID(), followerName);
        User followee = createUser(UUID.randomUUID(), "팔로우 대상");
        Follow follow = new Follow(follower, followee);
        FollowCommand command = new FollowCommand(followee.getId(), follower.getId());

        ReflectionTestUtils.setField(follow,"id", UUID.randomUUID());

        given(followUserReader.getById(follower.getId())).willReturn(follower);
        given(followUserReader.getById(followee.getId())).willReturn(followee);
        given(followRepository.save(any(Follow.class))).willReturn(follow);

        followSaver.save(command);

        ArgumentCaptor<UserFollowedEvent> captor = ArgumentCaptor.forClass(UserFollowedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue().followerName()).isEqualTo(followerName);
    }

    @Test
    @DisplayName("사용자 ID로 조회한 사용자들로 팔로우를 저장하고 생성 이벤트를 발행한다.")
    void save_loadsUsersAndPublishesEventFromCommand(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        User follower = createUser(followerId, "팔로워");
        User followee = createUser(followeeId, "팔로우 대상");

        FollowCommand command = new FollowCommand(followeeId, followerId);

        Follow saved = new Follow(follower, followee);
        ReflectionTestUtils.setField(saved, "id", followId);

        given(followUserReader.getById(followerId)).willReturn(follower);
        given(followUserReader.getById(followeeId)).willReturn(followee);
        given(followRepository.save(any(Follow.class))).willReturn(saved);

        FollowDto result = followSaver.save(command);

        assertThat(result).isEqualTo(new FollowDto(followId, followeeId, followerId));

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(captor.capture());

        assertThat(captor.getValue().getFollower()).isSameAs(follower);
        assertThat(captor.getValue().getFollowee()).isSameAs(followee);

        verify(eventPublisher).publishEvent(new UserFollowedEvent(followerId, "팔로워", followeeId));


    }

    @Test
    @DisplayName("팔로우 요청자가 없으면 사용자 없음 예외를 그대로 전달하고 저장과 이벤트 발행을 하지 않는다.")
    void save_throwsWhenFollowerNotFound(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);
        UserNotFoundException exception = new UserNotFoundException(Map.of("userId", followerId));

        given(followUserReader.getById(followerId)).willThrow(exception);

        assertThatThrownBy(()-> followSaver.save(command)).isSameAs(exception);

        verify(followUserReader).getById(followerId);
        verifyNoInteractions(followRepository, eventPublisher);
    }

    @Test
    @DisplayName("팔로우 대상이 없으면 대상없음 예외로 변환하고 저장과 이벤트 발행을 하지 않는다.")
    void save_throwsWhenFolloweeNotFound(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        User follower = createUser(followerId, "팔로워");
        FollowCommand command = new FollowCommand(followeeId, followerId);

        given(followUserReader.getById(followerId)).willReturn(follower);

        given(followUserReader.getById(followeeId))
                .willThrow(new UserNotFoundException(Map.of("userId", followeeId)));

        assertThatThrownBy(()-> followSaver.save(command))
                .isInstanceOfSatisfying(FollowTargetNotFoundException.class,
                        e -> assertThat(e.getDetails())
                                .containsExactlyEntriesOf(Map.of("followeeId", followeeId)));

        verify(followUserReader).getById(followerId);
        verify(followUserReader).getById(followeeId);
        verifyNoInteractions(followRepository, eventPublisher);
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
