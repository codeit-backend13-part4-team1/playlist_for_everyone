package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistCustomRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import java.util.List;

public class PlaylistCustomRepositoryImpl implements PlaylistCustomRepository {

  @Override
  public List<Playlist> search(PlaylistSearchCondition condition) {
    return List.of();
  }

  @Override
  public long count(PlaylistSearchCondition condition) {
    return 0;
  }
}
