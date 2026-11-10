package com.codeit.sb13.playlist_for_everyone.domain.user.repository;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, UserCustomRepository{

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /**
     * 권한·잠금 변경 중 같은 사용자에 대한 관리 작업이 겹치지 않도록
     * 조회한 사용자 행에 쓰기 잠금을 획득한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

}
