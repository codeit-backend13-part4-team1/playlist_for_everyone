package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistContent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from PlaylistContent pc where pc.playlistId = :playlistId")
  int deleteAllByPlaylistId(@Param("playlistId") UUID playlistId);

}
