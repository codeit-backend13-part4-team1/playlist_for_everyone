package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
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
@Slf4j
public class SseController {

    private final SseConnectionService sseConnectionService;

    /**
     * 현재 인증된 사용자에게 새 연결을 생성한다. 같은 사용자의 여러 탭도 각각 연결한다.
     *
     * <p>lastEventId는 제공된 프론트엔드의 재연결 쿼리이며 서비스의 초기 복구에 전달한다.
     * UUID 형식 오류는 메서드 진입 전에 400 JSON 응답으로 처리된다.
     * 생략하거나 본인 이력에서 찾을 수 없으면 기존 이력을 재전송하지 않고 현재 끝 위치를 기준으로 연결한다.
     *
     * @param lastEventId 클라이언트가 마지막으로 받은 이벤트 ID. 최초 연결에서는 생략 가능
     * @param user 보안 필터에서 인증한 사용자. 요청 파라미터로 수신자를 지정하지 않는다
     * @return Spring MVC가 비동기 응답에 연결할 emitter
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(value = "lastEventId", required = false) UUID lastEventId,
                              @AuthenticationPrincipal SecurityUser user){
        return sseConnectionService.connect(user.getUserId(), lastEventId);

    }


    /**
     * 초기 복구의 두 일시적 조회 실패를 스트림 시작 전 503 JSON 응답으로 변환한다.
     * 연결 서비스가 상태를 정리한 뒤 예외를 전달한다. 모든 데이터 접근 예외를 503으로 바꾸지는 않는다.
     * 연결이 이미 열린 뒤의 후속 조회 실패는 coordinator의 예약 재시도 경로가 담당한다.
     */
    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            QueryTimeoutException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInitialHistoryReadFailure(DataAccessException failure){
        log.error("SSE 초기 이력 조회에 실패했습니다.", failure);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        failure.getClass().getSimpleName(),
                        "SSE 이벤트 이력을 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                        Map.of()
                ));
    }
}
