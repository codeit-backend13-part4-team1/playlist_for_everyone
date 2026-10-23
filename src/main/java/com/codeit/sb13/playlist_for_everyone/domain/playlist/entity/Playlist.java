package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "playlists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist extends UpdatedAtEntity {

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @Column(name = "subscriber_count", nullable = false)
  private long subscriberCount;

  public Playlist(UUID ownerId, String title, String description) {
    this.ownerId = ownerId;
    this.title = title;
    this.description = description;
  }


}
