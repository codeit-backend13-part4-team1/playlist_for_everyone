package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemporaryPasswordRepository extends JpaRepository<TemporaryPassword, UUID> {

    /**
     * 사용자의 특정 상태 임시 비밀번호를 조회한다.
     * 재발급 시 기존 ACTIVE를 파기하고, 로그인 시 ACTIVE 중 만료 전인 것을 확인하는데 사용
     */
    List<TemporaryPassword> findAllByUserIdAndStatus(UUID userId, TemporaryPasswordStatus status);

}
