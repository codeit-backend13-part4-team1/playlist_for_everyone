package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * 기본 JPA 저장 기능과 대화방 전용 조회 계약을 함께 제공한다.
 * 응답에 필요한 참여자·최신 메시지·미읽음 조회와 검색·페이지 처리는 사용자 정의 구현체가 담당한다.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID>, ConversationCustomRepository {

    /**
     * DM 저장 후 DTO 변환에 필요한 두 참여자와 프로필을 함께 로딩한다.
     * 이 조회에는 요청자 조건이 없으므로 호출한 Reader가 별도로 참여 여부를 검증한다.
     */
    @EntityGraph(attributePaths = {
            "userA", "userA.profileImage",
            "userB", "userB.profileImage"
    })
    @Query("""
        SELECT c
        FROM Conversation c
        WHERE c.id =:conversationId
    """)
    Optional<Conversation> findDetailedById(UUID conversationId);
}
