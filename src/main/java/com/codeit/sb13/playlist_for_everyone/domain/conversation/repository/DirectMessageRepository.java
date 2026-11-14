package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> , DirectMessageCustomRepository{

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE DirectMessage dm
        SET dm.read = true,
            dm.updatedAt = :updatedAt
        WHERE
            dm.conversation.id = :conversationId
            AND dm.receiver.id = :receiverId
            AND dm.read = false
            AND EXISTS (
                SELECT referenceDm.id
                FROM DirectMessage referenceDm
                WHERE referenceDm.id = :directMessageId
                    AND referenceDm.conversation.id = :conversationId
                    AND (
                            dm.createdAt < referenceDm.createdAt
                            OR (
                                    dm.createdAt = referenceDm.createdAt
                                    AND dm.id <= referenceDm.id
                                )
                        )
                )
    """)
    void updateReadStatusUpToMessage(
            @Param("conversationId") UUID conversationId,
            @Param("receiverId") UUID receiverId,
            @Param("directMessageId") UUID directMessageId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Query("""
        SELECT COUNT(*) > 0
        FROM DirectMessage dm
        WHERE dm.id = :directMessageId
            AND dm.conversation.id = :conversationId
    """)
    boolean existsByIdAndConversationId(
            @Param("directMessageId") UUID directMessageId,
            @Param("conversationId") UUID conversationId
    );
}
