package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  private User owner;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @Column(name = "subscriber_count", nullable = false)
  private long subscriberCount;

  public Playlist(User owner, String title, String description) {
    this.owner = owner;
    this.title = title;
    this.description = description;
  }

  public void update(String title, String description) {
    if(title!= null) { this.title = title; }
    if(description!= null) { this.description = description; }
  }

  public boolean isOwnerBy(UUID userId) {
    return this.owner.equals(userId);
  }

}
