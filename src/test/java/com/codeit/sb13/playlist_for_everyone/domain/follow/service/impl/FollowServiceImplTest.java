package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.SelfFollowNotAllowedException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("자기 자신을 팔로우하면 예외가 발생하고 조회와 저장을 수행하지 않는다.")
    void follow_rejectsSelfFollow(){
        UUID userId = UUID.randomUUID();
        FollowCommand command = new FollowCommand(userId, userId);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOf(SelfFollowNotAllowedException.class);

        verifyNoInteractions(
                followRepository,
                userRepository,
                followSaver
        );
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
    @DisplayName("팔로우 대상 사용자가 없으면 잘못된 요청 예외가 발생하고 저장하지 않는다.")
    void follow_rejectMissingFollowee(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId))
                .willReturn(Optional.empty());

        User follower = mock(User.class);
        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(followeeId)).willReturn(Optional.empty());

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOfSatisfying(EveryOneException.class,
                        e -> assertThat(e.getApiErrorCode().getStatus())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(followSaver);
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

    @Test
    @DisplayName("팔로우 UNIQUE 충돌 후 기존 관계를 찾지 못하면 생성 실패 예외가 발생한다.")
    void follow_throwsWhenExistingFollowMissingAfterUniqueViolation(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        User follower = mock(User.class);
        User followee = mock(User.class);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
        given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId))
                .willReturn(Optional.empty());

        SQLException sqlException = new SQLException("duplicate follow", "23505");

        ConstraintViolationException constraintException = new ConstraintViolationException("duplicate follow", sqlException, "uq_follows_follower_followee");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("팔로우 중복 저장", constraintException);

        given(followSaver.save(any(Follow.class))).willThrow(exception);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOfSatisfying(FollowCreationFailedException.class,
                        e -> assertThat(e.getCause())
                                .isSameAs(exception));

        verify(followRepository, times(2))
                .findByFolloweeIdAndFollowerId(followeeId, followerId);
        verify(followSaver).save(any(Follow.class));
    }

    @Test
    @DisplayName("다른 UNIQUE 제약조건 위반이면 재조회 없이 팔로우 생성 실패 예외가 발생한다.")
    void follow_throwsWhenOtherUniqueConstraintViolated(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        User follower = mock(User.class);
        User followee = mock(User.class);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
        given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId))
                .willReturn(Optional.empty());


        SQLException sqlException = new SQLException("other unique violation", "23505");

        ConstraintViolationException constraintException = new ConstraintViolationException("other unique violation", sqlException, "other_unique_constraint");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("다른 UNIQUE 제약조건 위반", constraintException);

        given(followSaver.save(any(Follow.class))).willThrow(exception);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOfSatisfying(FollowCreationFailedException.class,
                        e-> assertThat(e.getCause())
                                .isSameAs(exception));

        verify(followRepository, times(1))
                .findByFolloweeIdAndFollowerId(followeeId, followerId);
        verify(followSaver).save(any(Follow.class));
    }

    @Test
    @DisplayName("제약조건 이름이 같아도 UNIQUE 위반 코드가 아니면 재조회 없이 생성 실패 예외가 발생한다.")
    void follow_throwsWhenSqlStateIsNotUniqueViolation(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        User follower = mock(User.class);
        User followee = mock(User.class);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
        given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId))
                .willReturn(Optional.empty());


        SQLException sqlException = new SQLException("none-unique constraint violation", "23503");

        ConstraintViolationException constraintException = new ConstraintViolationException("none-unique constraint violation", sqlException, "uq_follows_follower_followee");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("UNIQUE 이외의 제약조건 위반", constraintException);

        given(followSaver.save(any(Follow.class))).willThrow(exception);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOfSatisfying(FollowCreationFailedException.class,
                        e-> assertThat(e.getCause())
                                .isSameAs(exception));

        verify(followRepository, times(1))
                .findByFolloweeIdAndFollowerId(followeeId, followerId);

        verify(followSaver).save(any(Follow.class));
    }

    @Test
    @DisplayName("본인의 팔로우를 취소하면 해당 관계를 삭제한다.")
    void unfollow_deletesOwnFollow(){
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        UnFollowCommand command = new UnFollowCommand(followId, followerId);
        Follow follow = mock(Follow.class);

        given(followRepository.findById(followId)).willReturn(Optional.of(follow));
        given(follow.getFollowerId()).willReturn(followerId);
        followService.unfollow(command);

        verify(followRepository).findById(followId);
        verify(followRepository).delete(follow);
    }

}