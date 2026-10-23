package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "playlist_contents",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_PLAYLIST_CONTENTS_PLAYLIST_CONTENT",
            columnNames = {"playlist_id", "content_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistContent extends CreatedAtEntity {

  @Column(name = "playlist_id", nullable = false)
  private UUID playlistId;

  @Column(name = "content_id", nullable = false)
  private UUID contentId;

  public PlaylistContent(UUID playlistId, UUID contentId) {
    this.playlistId = playlistId;
    this.contentId = contentId;
  }
}

