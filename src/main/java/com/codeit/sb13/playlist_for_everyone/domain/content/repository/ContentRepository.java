package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID> {
}
