package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 서비스")
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowSaver followSaver;

    @InjectMocks
    private FollowServiceImpl followService;

    @Test
    @DisplayName("새로운 사용자를 팔로우하면 관계를 저장하고 생성 결과를 반환한다.")
    void follow_createsNewFollow(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);
        given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId))
                .willReturn(Optional.empty());

        Follow savedFollow = mock(Follow.class);
        User follower = mock(User.class);
        User followee = mock(User.class);

        given(savedFollow.getId()).willReturn(followId);
        given(follower.getId()).willReturn(followerId);
        given(followee.getId()).willReturn(followeeId);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));

        given(savedFollow.getFollowerId()).willReturn(followerId);
        given(savedFollow.getFolloweeId()).willReturn(followeeId);
        given(followSaver.save(any(Follow.class))).willReturn(savedFollow);

        FollowResult result = followService.follow(command);

        assertThat(result).isNotNull();
        assertThat(result.created()).isTrue();
        assertThat(result.followDto()).isEqualTo(new FollowDto(followId, followeeId, followerId));

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);

        verify(followSaver).save(captor.capture());

        Follow requestedFollow = captor.getValue();
        assertThat(requestedFollow.getFolloweeId()).isEqualTo(followeeId);
        assertThat(requestedFollow.getFollowerId()).isEqualTo(followerId);

    }

    @Test
    @DisplayName("이미 팔로우 중이면 추가 저장없이 기존 팔로우를 반환한다")
    void follow_returnsExistingFollow(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();


        FollowCommand command = new FollowCommand(followeeId, followerId);

        Follow existingFollow = mock(Follow.class);

        given(existingFollow.getId()).willReturn(followId);
        given(existingFollow.getFollowerId()).willReturn(followerId);
        given(existingFollow.getFolloweeId()).willReturn(followeeId);

        given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId))
                .willReturn(Optional.of(existingFollow));

        FollowResult result = followService.follow(command);

        assertThat(result).isNotNull();
        assertThat(result.created()).isFalse();
        assertThat(result.followDto()).isEqualTo(new FollowDto(followId, followeeId, followerId));
        verify(followRepository)
                .findByFolloweeIdAndFollowerId(followeeId, followerId);

        verifyNoInteractions(userRepository, followSaver);

    }

    @Test
    @DisplayName("팔로우 UNIQUE 충돌이 발생하면 기존 관계를 재조회하여 반환한다.")
    void follow_returnsExistingFollowAfterUniqueViolation(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        User follower = mock(User.class);
        User followee = mock(User.class);
        Follow existingFollow = mock(Follow.class);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));

        given(existingFollow.getId()).willReturn(followId);
        given(existingFollow.getFollowerId()).willReturn(followerId);
        given(existingFollow.getFolloweeId()).willReturn(followeeId);

        given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existingFollow));

        SQLException sqlException = new SQLException("duplicate follow", "23505");

        ConstraintViolationException constraintException = new ConstraintViolationException("duplicate follow", sqlException, "uq_follows_follower_followee");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("팔로우 중복 저장", constraintException);

        given(followSaver.save(any(Follow.class))).willThrow(exception);

        FollowResult result = followService.follow(command);
        assertThat(result).isNotNull();
        assertThat(result.created()).isFalse();
        assertThat(result.followDto()).isEqualTo(new FollowDto(followId, followeeId, followerId));

        verify(followRepository, times(2)).findByFolloweeIdAndFollowerId(followeeId, followerId);

        verify(followSaver).save(any(Follow.class));

    }

}