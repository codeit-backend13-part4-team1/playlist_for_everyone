package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.SelfFollowNotAllowedException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 서비스")
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private FollowSaver followSaver;

    @Mock
    private FollowReader followReader;

    @InjectMocks
    private FollowServiceImpl followService;

    @Test
    @DisplayName("새로운 사용자를 팔로우하면 저장 컴포넌트에 명령을 전달하고 생성 결과를 반환한다.")
    void follow_createsNewFollow(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);
        FollowDto savedFollow = new FollowDto(followId, followeeId, followerId);

        given(followReader.findExistingFollow(command)).willReturn(Optional.empty());
        given(followSaver.save(command)).willReturn(savedFollow);

        FollowResult result = followService.follow(command);

        assertThat(result).isNotNull();
        assertThat(result.created()).isTrue();
        assertThat(result.followDto()).isEqualTo(new FollowDto(followId, followeeId, followerId));

        verify(followReader).findExistingFollow(command);
        verify(followSaver).save(command);
    }

    @Test
    @DisplayName("자기 자신을 팔로우하면 예외가 발생하고 조회와 저장을 수행하지 않는다.")
    void follow_rejectsSelfFollow(){
        UUID userId = UUID.randomUUID();
        FollowCommand command = new FollowCommand(userId, userId);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOf(SelfFollowNotAllowedException.class);

        verifyNoInteractions(
                followReader,
                followRepository,
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

        FollowResult existingFollow = FollowResult.alreadyFollowed(new FollowDto(followId, followeeId, followerId));

        given(followReader.findExistingFollow(command))
                .willReturn(Optional.of(existingFollow));

        FollowResult result = followService.follow(command);

        assertThat(result).isNotNull();
        assertThat(result.created()).isFalse();
        assertThat(result.followDto()).isEqualTo(new FollowDto(followId, followeeId, followerId));
        verify(followReader).findExistingFollow(command);

        verifyNoInteractions( followSaver);

    }

    @Test
    @DisplayName("저장 컴포넌트에서 팔로우 대상 없음 예외가 발생하면 그대로 전달한다.")
    void follow_rejectMissingFollowee(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);
        FollowTargetNotFoundException exception = new FollowTargetNotFoundException(Map.of("followeeId", followeeId));

        given(followReader.findExistingFollow(command))
                .willReturn(Optional.empty());

        given(followSaver.save(command)).willThrow(exception);

        assertThatThrownBy(()-> followService.follow(command)).isSameAs(exception);

        verify(followReader).findExistingFollow(command);
        verify(followSaver).save(command);
    }

    @Test
    @DisplayName("팔로우 UNIQUE 충돌이 발생하면 기존 관계를 재조회하여 반환한다.")
    void follow_returnsExistingFollowAfterUniqueViolation(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        FollowResult existingFollow = FollowResult.alreadyFollowed(new FollowDto(followId, followeeId, followerId));

        given(followReader.findExistingFollow(command))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existingFollow));

        SQLException sqlException = new SQLException("duplicate follow", "23505");

        ConstraintViolationException constraintException = new ConstraintViolationException("duplicate follow", sqlException, "uq_follows_follower_followee");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("팔로우 중복 저장", constraintException);

        given(followSaver.save(command)).willThrow(exception);

        FollowResult result = followService.follow(command);
        assertThat(result).isNotNull();
        assertThat(result.created()).isFalse();
        assertThat(result.followDto()).isEqualTo(new FollowDto(followId, followeeId, followerId));

        verify(followReader, times(2)).findExistingFollow(command);

        verify(followSaver).save(command);

    }

    @Test
    @DisplayName("팔로우 UNIQUE 충돌 후 기존 관계를 찾지 못하면 생성 실패 예외가 발생한다.")
    void follow_throwsWhenExistingFollowMissingAfterUniqueViolation(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        given(followReader.findExistingFollow(command))
                .willReturn(Optional.empty());

        SQLException sqlException = new SQLException("duplicate follow", "23505");

        ConstraintViolationException constraintException = new ConstraintViolationException("duplicate follow", sqlException, "uq_follows_follower_followee");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("팔로우 중복 저장", constraintException);

        given(followSaver.save(command)).willThrow(exception);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOfSatisfying(FollowCreationFailedException.class,
                        e -> assertThat(e.getCause())
                                .isSameAs(exception));

        verify(followReader, times(2))
                .findExistingFollow(command);
        verify(followSaver).save(command);
    }

    @Test
    @DisplayName("다른 UNIQUE 제약조건 위반이면 재조회 없이 팔로우 생성 실패 예외가 발생한다.")
    void follow_throwsWhenOtherUniqueConstraintViolated(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        given(followReader.findExistingFollow(command))
                .willReturn(Optional.empty());

        SQLException sqlException = new SQLException("other unique violation", "23505");

        ConstraintViolationException constraintException = new ConstraintViolationException("other unique violation", sqlException, "other_unique_constraint");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("다른 UNIQUE 제약조건 위반", constraintException);

        given(followSaver.save(command)).willThrow(exception);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOfSatisfying(FollowCreationFailedException.class,
                        e-> assertThat(e.getCause())
                                .isSameAs(exception));

        verify(followReader, times(1))
                .findExistingFollow(command);
        verify(followSaver).save(command);
    }

    @Test
    @DisplayName("제약조건 이름이 같아도 UNIQUE 위반 코드가 아니면 재조회 없이 생성 실패 예외가 발생한다.")
    void follow_throwsWhenSqlStateIsNotUniqueViolation(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);

        given(followReader.findExistingFollow(command))
                .willReturn(Optional.empty());

        SQLException sqlException = new SQLException("none-unique constraint violation", "23503");

        ConstraintViolationException constraintException = new ConstraintViolationException("none-unique constraint violation", sqlException, "uq_follows_follower_followee");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("UNIQUE 이외의 제약조건 위반", constraintException);

        given(followSaver.save(command)).willThrow(exception);

        assertThatThrownBy(()-> followService.follow(command))
                .isInstanceOfSatisfying(FollowCreationFailedException.class,
                        e-> assertThat(e.getCause())
                                .isSameAs(exception));

        verify(followReader, times(1))
                .findExistingFollow(command);

        verify(followSaver).save(command);
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


    @Test
    @DisplayName("팔로우 관계가 없으면 예외 없이 종료하고 삭제하지 않는다")
    void unfollow_ignoresMissingFollow(){
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        UnFollowCommand command = new UnFollowCommand(followId, followerId);

        given(followRepository.findById(followId)).willReturn(Optional.empty());

        assertThatCode(()-> followService.unfollow(command))
                .doesNotThrowAnyException();

        verify(followRepository).findById(followId);
        verify(followRepository,never()).delete(any(Follow.class));
    }

    @Test
    @DisplayName("팔로우 관계가 있으면 관계 ID와 사용자 ID를 담은 DTO를 반환한다.")
    void findFollowDto_returnsExistingFollow(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);
        FollowDto expected = new FollowDto(followId, followeeId, followerId);

        given(followReader.findExistingFollow(command)).willReturn(Optional.of(FollowResult.alreadyFollowed(expected)));
        FollowDto result = followService.findFollowDto(command);

        assertThat(result).isEqualTo(expected);

        verify(followReader).findExistingFollow(command);

        verifyNoInteractions(followSaver);
    }

    @Test
    @DisplayName("팔로우 관계가 없으면 대상 사용자 ID를 담은 조회 실패 예외가 발생한다.")
    void findFollowDto_throwsWhenFollowNotFound(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, followerId);
        given(followReader.findExistingFollow(command)).willReturn(Optional.empty());

        assertThatThrownBy(()-> followService.findFollowDto(command))
                .isInstanceOfSatisfying(FollowNotFoundException.class,
                        e -> {
                    assertThat(e.getApiErrorCode().getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND);

                    assertThat(e.getDetails())
                                .containsEntry("followeeId", followeeId);
                });

        verify(followReader).findExistingFollow(command);
        verifyNoInteractions( followSaver, followRepository);
    }

    @Test
    @DisplayName("대상 사용자의 팔로워 수를 조회하면 조회된 개수를 반환한다.")
    void countFollowers_returnFollowerCount(){
        UUID followeeId = UUID.randomUUID();

        given(followReader.countFollowers(followeeId)).willReturn(3L);

        Long result = followService.countFollowers(followeeId);
        assertThat(result).isEqualTo(3L);

        verify(followReader).countFollowers(followeeId);
        verifyNoInteractions( followSaver, followRepository);
    }

    @Test
    @DisplayName("팔로워 ID 목록을 조회하면 해당 사용자의 팔로워 ID들을 반환한다.")
    void findFollowerIds_returnsFollowerIds(){
        UUID followeeId = UUID.randomUUID();
        List<UUID> expected = List.of(UUID.randomUUID(), UUID.randomUUID());

        given(followReader.findFollowerIds(followeeId)).willReturn(expected);

        List<UUID> result = followService.findFollowerIds(followeeId);

        assertThat(result).containsExactlyInAnyOrderElementsOf(expected);

        verify(followReader).findFollowerIds(followeeId);
        verifyNoInteractions(followSaver, followRepository);
    }

}