package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Subscription;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  boolean existsByPlaylistIdAndSubscriberId(UUID playlistId, UUID subscriberId);

  @Query("select s.playlistId from Subscription s "
      + "where s.subscriberId = :subscriberId and s.playlistId in :playlistIds")
  List<UUID> findSubscribedPlaylistIds(
      @Param("subscriberId") UUID subscriberId,
      @Param("playlistIds") Collection<UUID> playlistIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Subscription s where s.playlistId = :playlistId")
  int deleteAllByPlaylistId(@Param("playlistId") UUID playlistId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Subscription s "
      + "where s.playlistId = :playlistId and s.subscriberId = :subscriberId")
  int deleteByPlaylistIdAndSubscriberId(
      @Param("playlistId") UUID playlistId,
      @Param("subscriberId") UUID subscriberId);
}