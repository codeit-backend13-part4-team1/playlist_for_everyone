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
