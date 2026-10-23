package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PlaylistServiceImplTest {

  @Mock
  private PlaylistRepository playlistRepository;

  @InjectMocks
  private PlaylistServiceImpl playlistService;

  @Test
  @DisplayName("요청한 값과 소유자 ID로 플레이리스트를 저장한다.")
  void 플레이리스트_생성시_요청값과_소유자로_저장한다() {
    // given
    UUID ownerId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("제목", "설명");
    given(playlistRepository.save(any(Playlist.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    playlistService.create(ownerId, request);

    // then
    ArgumentCaptor<Playlist> captor = ArgumentCaptor.forClass(Playlist.class);
    verify(playlistRepository).save(captor.capture());
    Playlist saved = captor.getValue();
    assertThat(saved.getOwnerId()).isEqualTo(ownerId);
    assertThat(saved.getTitle()).isEqualTo("제목");
    assertThat(saved.getDescription()).isEqualTo("설명");


  }

}
