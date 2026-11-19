package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessageNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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
 * <p>단건 조회도 REQUIRED를 사용한다. 동기식 AFTER_COMMIT 콜백에서는 아직 바인딩된 저장 측 자원을
 * 재사용하여, 저장 연결을 보유한 채 두 번째 연결을 기다리는 상황을 피한다.
 * 조회 결과의 DTO 변환과 SSE 발행은 호출자가 수행하며, 콜백이 끝나기 전 저장 연결이 반환된다고 보장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageReader {

    private final DirectMessageRepository directMessageRepository;
    private final EntityManager entityManager;

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
     * 커밋 후 이벤트 처리에 필요한 메시지·송수신자·프로필을 함께 조회한다.
     * REQUIRED이므로 상위 트랜잭션이 없으면 읽기 전용 트랜잭션을 시작하고, 있으면 참여한다.
     * 동기식 커밋 후 콜백에서는 정리 전 저장 측 자원을 재사용한다.
     * 커밋 후 콜백에서는 새 물리 트랜잭션이나 readOnly 속성 변경을 보장하지 않으며 조회만 수행한다.
     * 이 경로에 쓰기를 추가하면 후속 커밋이 없으므로 별도의 트랜잭션 경계를 설계해야 한다.
     *
     * <p>상세 조회만으로는 영속성 컨텍스트에 남은 저장 전 필드 값이 바뀌지 않는다.
     * 같은 연결에서 메시지를 refresh하여 DB 정밀도로 저장된 생성 시각을 응답에 사용한다.
     * 추가 SELECT가 발생하며 해당 메시지의 메모리 상태를 덮어쓰므로, 커밋된 메시지 조회에 사용한다.
     * 영속성 컨텍스트 전체를 비우거나 다른 엔티티를 명시적으로 갱신하지 않는다.
     *
     * <p>메시지 ID로 조회하는 내부 처리용 메서드이며 요청자의 대화방 참여 여부를 검증하지 않는다.
     * 사용자 요청에서 호출한다면 호출자가 접근 권한을 먼저 확인해야 한다.
     * 미존재 시 DirectMessageNotFoundException을 던지고, 조회 실패의 처리 여부는 호출자가 결정한다.
     */
    @Transactional(readOnly = true)
    public DirectMessage getById(UUID directMessageId){
        DirectMessage message = directMessageRepository.findDetailedById(directMessageId)
                .orElseThrow(() -> new DirectMessageNotFoundException(
                        Map.of("directMessageId", directMessageId)));
        entityManager.refresh(message);
        return message;
    }
}
