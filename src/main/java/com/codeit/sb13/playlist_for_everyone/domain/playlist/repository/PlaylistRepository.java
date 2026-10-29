package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository
    extends JpaRepository<Playlist, UUID>, PlaylistCustomRepository {

  @Query("select p from Playlist p join fetch p.owner where p.id = :id")
  Optional<Playlist> findWithOwnerById(@Param("id") UUID id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Playlist p set p.updatedAt = :now where p.id = :playlistId")
  int touchUpdatedAt(@Param("playlistId") UUID playlistId, @Param("now") LocalDateTime now);

}
