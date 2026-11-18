package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;

/**
 * 브로커에서 수신한 DM을 이 서버의 대화방 구독자에게 전달하는 계약이다.
 * DB 재저장·읽음 처리·서버 간 재발행은 하지 않는다. 구독자 참여 검증은 인바운드 인가에서 수행한다.
 */
public interface DirectMessageDeliveryService {

    void sendToLocalSubscribers(MessageDto message);
}
