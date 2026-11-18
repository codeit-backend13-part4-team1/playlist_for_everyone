package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationPair;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;

import java.util.List;
import java.util.Optional;

/**
 * 생성 응답과 조회 API가 공통으로 사용하는 대화방 조회 계약이다.
 * 엔티티와 계산된 미읽음 여부를 내부 조회 결과로 반환하며, HTTP 응답 변환은 호출 계층에서 수행한다.
 */
public interface ConversationCustomRepository {

    /** 대화방 ID와 요청자 참여 조건을 함께 적용하며, 미존재·비참여 모두 빈 Optional을 반환한다. */
    Optional<ConversationDetailProjection> findDetailById(ConversationFindByIdCondition condition);

    /**
     * 정렬된 사용자 쌍과 일치하는 대화방을 조회한다.
     * 호출자는 요청자가 해당 두 사용자 중 한 명인 쌍을 전달해야 하며, 결과가 없으면 빈 Optional을 반환한다.
     */
    Optional<ConversationDetailProjection> findDetailByUserPair(ConversationPair pair);

    /**
     * 본인 참여·검색·커서 조건을 만족하는 대화방을 정렬하여 최대 limit + 1건 반환한다.
     * 검색된 메시지와 관계없이 실제 최신 메시지와 요청자 기준 미읽음 여부를 함께 조회한다.
     */
    List<ConversationDetailProjection> search(ConversationSearchCondition condition);

    /** 본인 참여·검색 조건을 만족하는 전체 개수이며, 커서와 페이지 크기는 적용하지 않는다. */
    long searchCount(ConversationSearchCondition condition);
}
