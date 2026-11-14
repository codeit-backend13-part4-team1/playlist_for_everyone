package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;

import java.util.List;

/** 대화방 ID와 메시지의 발신자·수신자 조건을 적용하는 DM 목록 조회 계약이다. */
public interface DirectMessageCustomRepository {
    /**
     * 생성 시각·ID를 같은 방향으로 정렬하고 커서 이후 최대 limit + 1건을 반환한다.
     * 응답 변환에 필요한 발신자·수신자·프로필을 함께 조회하며, 초과 항목 제거와 다음 커서 결정은 서비스가 맡는다.
     */
    List<DirectMessage> search(DirectMessageSearchCondition condition);

    /** 목록과 같은 대화방·메시지 참여 조건의 전체 개수다. 커서와 limit은 적용하지 않는다. */
    long searchCount(DirectMessageSearchCondition condition);
}
