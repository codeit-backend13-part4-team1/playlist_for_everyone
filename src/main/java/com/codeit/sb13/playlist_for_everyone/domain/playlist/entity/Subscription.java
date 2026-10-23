package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
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
    name = "subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_SUBSCRIPTIONS_PLAYLIST_SUBSCRIBER",
            columnNames = {"playlist_id", "subscriber_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends CreatedAtEntity {

  @Column(name = "playlist_id", nullable = false)
  private UUID playlistId;

  @Column(name = "subscriber_id", nullable = false)
  private UUID subscriberId;

  public Subscription(UUID playlistId, UUID subscriberId) {
    this.playlistId = playlistId;
    this.subscriberId = subscriberId;
  }

}
