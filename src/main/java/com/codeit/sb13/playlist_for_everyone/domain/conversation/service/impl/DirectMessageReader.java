package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM 단건·목록·전체 개수 조회의 읽기 전용 트랜잭션을 담당한다.
 *
 * <p>목록과 개수 조회는 REQUIRED를 사용하므로 상위 트랜잭션이 있으면 참여하고, 없으면 시작한다.
 * 목록과 개수는 별도 메서드 호출이며, 두 조회가 항상 같은 시점의 데이터를 본다고 보장하지 않는다.
 * 대화방 참여 검증과 다음 커서·응답 구성은 호출한 서비스가 담당한다.
 *
 * <p>단건 조회는 REQUIRES_NEW를 사용하여 별도의 읽기 전용 트랜잭션으로 실행한다.
 * 저장 커밋 이후 이벤트 처리에서도 새 트랜잭션으로 메시지와 사용자·프로필을 조회할 수 있다.
 * 조회 결과의 DTO 변환과 SSE 발행은 이 메서드가 반환된 뒤 호출한 쪽에서 수행한다.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageReader {

    private final DirectMessageRepository directMessageRepository;

    /** 다음 페이지 판단을 위한 초과 한 건과 응답 변환에 필요한 사용자·프로필을 함께 조회한다. */
    @Transactional(readOnly = true)
    public List<DirectMessage> search(DirectMessageSearchCondition condition){
        return directMessageRepository.search(condition);
    }

    /** 같은 대화방·메시지 참여 조건으로 전체 개수를 조회하며 커서와 페이지 크기는 적용하지 않는다. */
    @Transactional(readOnly = true)
    public long searchCount(DirectMessageSearchCondition condition){
       return directMessageRepository.searchCount(condition);
    }

    /**
     * 커밋 후 이벤트 처리에 필요한 메시지·송수신자·프로필을 독립 읽기 트랜잭션에서 조회한다.
     * 저장 측 트랜잭션에 참여하지 않으며, 반환 이후의 DTO·프로필 URL 변환은 호출자가 수행한다.
     *
     * <p>메시지 ID로 조회하는 내부 처리용 메서드이며 요청자의 대화방 참여 여부를 검증하지 않는다.
     * 사용자 요청에서 호출한다면 호출자가 접근 권한을 먼저 확인해야 한다.
     * 미존재 시 DirectMessageNotFoundException을 던지고, 조회 실패의 처리 여부는 호출자가 결정한다.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public DirectMessage getById(UUID directMessageId){
        return directMessageRepository.findDetailedById(directMessageId)
                .orElseThrow(() -> new DirectMessageNotFoundException(
                        Map.of("directMessageId", directMessageId)));
    }
}
