package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import java.util.UUID;

/**
 * 인증 정보의 요청자 ID와 요청 본문의 상대방 ID를 서비스에 전달한다.
 *
 * <p>호출자는 두 ID의 필수 값 검증을 완료해야 한다. 요청자 기준의 상대방·미읽음 상태를
 * 계산할 수 있도록 요청 방향을 유지하며, 저장·조회용 사용자 쌍 정렬은 각 처리 단계에서 수행한다.
 */
public record ConversationCreateCommand(UUID requesterId, UUID withUserId) {
}
