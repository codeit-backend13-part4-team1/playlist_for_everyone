package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;

/**
 * 참여한 대화방의 DM 목록 조회와 누적 읽음 처리를 제공한다.
 * 호출자는 인증된 요청자 ID와 입력 검증을 마친 조건을 전달하며, 두 기능 모두 대화방 참여 여부를 먼저 확인한다.
 */
public interface DirectMessageService {

    /**
     * 기준 메시지까지 요청자가 받은 미읽음 메시지만 읽음 처리한다.
     *
     * <p>자신이 보낸 메시지도 기준으로 사용할 수 있다. 대화방 미존재·비참여는 대화방 미존재 예외로,
     * 기준 메시지 미존재·다른 대화방 소속은 메시지 미존재 예외로 거절한다.
     * 갱신 대상이 없어도 정상 종료하며, 반복 요청으로 이미 읽은 메시지의 수정 시각을 바꾸지 않는다.
     *
     * @param command 대상 대화방, 기준 메시지와 인증된 요청자 ID
     */
    void read(DirectMessageReadCommand command);

    /**
     * 대화방의 메시지를 생성 시각·ID 순서로 조회하고 커서 응답을 구성한다.
     *
     * <p>조회로 읽음 상태를 바꾸지 않는다. totalCount에는 커서·limit을 적용하지 않으며,
     * 마지막 페이지의 두 커서는 null이다. 대화방이 없거나 요청자가 참여하지 않으면 같은 미존재 예외를 전달한다.
     *
     * @param condition 필수 값·양수 limit·정렬값·커서 형식과 조합 검증을 마친 조회 조건
     * @return 메시지 목록, 다음 페이지 정보, 전체 메시지 개수와 적용한 정렬 조건
     */
    CursorResponse<MessageDto> search(DirectMessageSearchCondition condition);
}
