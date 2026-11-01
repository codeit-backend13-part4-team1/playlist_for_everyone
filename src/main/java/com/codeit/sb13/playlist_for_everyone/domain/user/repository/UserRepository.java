package com.codeit.sb13.playlist_for_everyone.domain.user.repository;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

}
