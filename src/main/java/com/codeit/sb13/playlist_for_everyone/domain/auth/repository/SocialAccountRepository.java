package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialAccount;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {


    /**
     * 제공자와 회원 ID로 연결 정보를 찾는다.
     * 로그인 처리에서 사용자 잠금 여부를 바로 확인하므로 사용자를 함께 조회한다.
     */
    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
