package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/** DM 영속성·조회와 기준 메시지까지의 누적 읽음 갱신을 제공한다. 읽음 갱신에는 쓰기 트랜잭션이 필요하다. */
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> , DirectMessageCustomRepository{

    /**
     * 같은 대화방에서 수신자가 receiverId인 미읽음 메시지 중 기준 이하의 메시지를 한 번에 갱신한다.
     *
     * <p>기준보다 생성 시각이 이르거나, 같은 시각이면 ID가 기준 ID 이하인 메시지를 포함한다.
     * 기준 메시지 자체의 발신·수신 방향은 제한하지 않으므로 자신이 보낸 메시지도 기준으로 사용할 수 있다.
     * 기준이 해당 대화방에 없으면 갱신되지 않으며, 이를 404로 구분하는 사전 검증은 Updater가 맡는다.
     *
     * <p>벌크 쿼리는 엔티티 감사 콜백에 의존하지 않고 updatedAt을 직접 기록한다.
     * read = false 조건으로 이미 읽은 메시지를 제외하여 반복·이전 기준 요청에서도 그 수정 시각을 유지한다.
     *
     * <p>자동 flush로 영속성 컨텍스트의 미반영 변경을 먼저 반영하고, 갱신 후 자동 clear로 컨텍스트 전체를 비운다.
     * 이미 참조하던 엔티티 객체의 필드가 자동으로 바뀌는 것은 아니며, 최신 상태가 필요하면 다시 조회해야 한다.
     */
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

    /** 기준 메시지가 요청한 대화방에 존재하는지 함께 검사한다. 미존재·다른 대화방 소속 모두 false다. */
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
