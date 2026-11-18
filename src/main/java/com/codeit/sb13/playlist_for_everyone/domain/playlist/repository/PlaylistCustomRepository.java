package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import java.util.List;

public interface PlaylistCustomRepository {
  List<Playlist> search(PlaylistSearchCondition condition);

  long count(PlaylistSearchCondition condition);

}
