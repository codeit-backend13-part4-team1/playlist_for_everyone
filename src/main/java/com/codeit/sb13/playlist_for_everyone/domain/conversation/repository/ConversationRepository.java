package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * 정렬된 사용자 쌍의 대화방, 최신 메시지, 요청자 기준 미읽음 여부를 함께 조회한다.
     *
     * <p>LEFT JOIN은 메시지가 없는 대화방도 반환하기 위한 것이다. 더 최신인 메시지가
     * 존재하지 않는 행을 골라 생성 시각 내림차순, 동률이면 ID 내림차순의 첫 메시지를 선택한다.
     * 미읽음 여부는 최신 메시지와 별개로 요청자가 받은 모든 메시지 중 읽지 않은 것이 있는지 판단한다.
     *
     * <p>EntityGraph는 두 참여자와 각 프로필 이미지 정보를 함께 조회해 응답 변환에 준비한다.
     * SELECT의 conversation, lastMessage, hasUnread 별칭은 Projection의 getter와 대응한다.
     *
     * @param userAId UUID 표준 문자열 사전순으로 앞선 참여자 ID
     * @param userBId UUID 표준 문자열 사전순으로 뒤의 참여자 ID
     * @param requesterId 두 참여자 중 실제 요청자 ID로, 미읽음 판단에 사용
     * @return 해당 사용자 쌍의 조회 결과. 대화방이 없으면 빈 Optional
     */
    @EntityGraph(attributePaths = {
            "userA", "userA.profileImage",
            "userB", "userB.profileImage",
    })
    @Query("""
    SELECT c AS conversation,
        dm AS lastMessage,
            CASE WHEN EXISTS (
                SELECT 1
                FROM DirectMessage unread
                WHERE unread.conversation = c
                    AND unread.receiver.id = :requesterId
                    AND unread.read = false
                ) THEN true
                  ELSE false
            END AS hasUnread
    FROM Conversation c
    LEFT JOIN DirectMessage dm
        ON dm.conversation = c
            AND NOT EXISTS (
                SELECT 1
                FROM DirectMessage newer
                WHERE newer.conversation = c
                    AND (
                            newer.createdAt > dm.createdAt
                                OR (
                                    newer.createdAt = dm.createdAt
                                        AND newer.id > dm.id
                                    )
                        )
            )
    WHERE c.userA.id = :userAId
        AND c.userB.id = :userBId
    """)
    Optional<ConversationDetailProjection> findDetailByUserPair(
            @Param("userAId") UUID userAId ,
            @Param("userBId") UUID userBId ,
            @Param("requesterId") UUID requesterId);

}
