package com.codeit.sb13.playlist_for_everyone.domain.auth.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 임시 비밀번호 발급이 커밋된 뒤 안내 메일을 보내기 위한 이벤트
 *
 * <p>메일에 넣을 원문 비밀번호를 담고 있어 DB나 메시지 브로커에 저장하지 않고 메모리로만 전달한다.
 * 로그에 찍혀도 원문과 이메일이 남지 않도록 toString에서 ID만 출력한다.
 * 만료 시각은 DB에 저장한 값과 같다. 메일에는 보내는 시점에 남은 시간을 적는다.
 */
public record TemporaryPasswordIssuedEvent(
        UUID temporaryPasswordId,
        String email,
        String temporaryPassword,
        LocalDateTime expiredAt
) {

    @Override
    public String toString() {
        return "TemporaryPasswordIssuedEvent[temporaryPasswordId=" + temporaryPasswordId + "]";
    }
}
