package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 서비스")
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

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
        given(savedFollow.getId()).willReturn(followId);
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
}