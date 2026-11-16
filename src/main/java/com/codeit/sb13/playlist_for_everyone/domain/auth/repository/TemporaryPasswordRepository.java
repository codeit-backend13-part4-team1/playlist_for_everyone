package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TemporaryPasswordRepository extends JpaRepository<TemporaryPassword, UUID> {

    /**
     * 사용자의 특정 상태 임시 비밀번호를 조회한다.
     * 재발급 시 기존 ACTIVE를 파기하고, 로그인 시 ACTIVE 중 만료 전인 것을 확인하는데 사용
     */
    List<TemporaryPassword> findAllByUserIdAndStatus(UUID userId, TemporaryPasswordStatus status);


    // 기준 시각 이후 발급 기록을 최신순으로 최대 5개. 파기·만료된 기록도 발급 횟수에 포함한다.
    List<TemporaryPassword> findTop5ByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime after);

}
