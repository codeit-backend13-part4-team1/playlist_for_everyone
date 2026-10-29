package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 인증된 사용자의 HTTP 응답을 열어 두고 서버 이벤트를 전달하는 SSE 연결 진입점이다.
 *
 * <p>일반 JSON 응답과 달리 SseEmitter를 반환하면 Spring MVC가 비동기 응답을 관리한다.
 * 연결을 맺은 요청 스레드가 계속 대기하는 구조가 아니며, 이후 전송 서비스가 emitter에
 * 이벤트를 보내면 같은 HTTP 응답에 데이터가 추가된다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
public class SseController {

    private final SseConnectionService sseConnectionService;

    /**
     * 현재 인증된 사용자에게 새 연결을 생성한다. 같은 사용자의 여러 탭도 각각 연결한다.
     *
     * <p>lastEventId는 제공된 프론트엔드의 재연결 쿼리 이름이다. 현재는 선택적으로 받고
     * UUID 형식만 검증한다. 형식 오류는 메서드 진입 전에 400 JSON 응답으로 처리된다.
     * 값은 아직 서비스로 전달하지 않으며, 이력을 조회해 누락 이벤트를 보내는 기능은
     * 후속 이슈 #53에서 연결한다.
     *
     * @param lastEventId 클라이언트가 마지막으로 받은 이벤트 ID. 최초 연결에서는 생략 가능
     * @param user 보안 필터에서 인증한 사용자. 요청 파라미터로 수신자를 지정하지 않는다
     * @return Spring MVC가 비동기 응답에 연결할 emitter
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(value = "lastEventId", required = false) UUID lastEventId,
                              @AuthenticationPrincipal SecurityUser user){
        return sseConnectionService.connect(user.getUserId());

    }
}
