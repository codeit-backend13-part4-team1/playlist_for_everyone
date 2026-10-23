package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Subscription;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
}