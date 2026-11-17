package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * WebSocket 인바운드 메시지의 종류와 목적지별 접근 규칙을 구성한다.
 *
 * <p>DM 경로의 대화방 참여 검증은 DirectMessageAuthorizationManager에 위임한다.
 * 콘텐츠 경로와 연결 관리 메시지의 통과 규칙도 이곳에서 구성하며,
 * 시청 세션 관리와 콘텐츠 업무 검증은 각 담당 컴포넌트가 수행한다.
 */
@Configuration(proxyBeanMethods = false)
public class WebSocketAuthorizationConfig {

    /*
     * TODO(콘텐츠 담당자와 협의): 콘텐츠 전용 채널 인가 정책을 확정한다.
     * 기존 CONNECT 인증과 콘텐츠 컨트롤러·서비스의 검사는 유지한다.
     * 아래 항목은 추가·보강 후보이며, 적용 범위와 방식은 협의 후 결정한다.
     *
     * - 채팅 SEND와 시청·채팅 SUBSCRIBE의 허용 경로, UUID 형식,
     *   패턴 구독 제한을 채널 단계에서 검증할 범위
     * - 클라이언트의 /sub/contents/** 직접 SEND 등 목적지 방향 우회 차단
     * - 역할·계정 상태·콘텐츠 존재 여부 검증의 채널과 업무 로직 간 역할 분담
     * - 채팅 전송에 해당 콘텐츠의 시청 또는 채팅 구독 참여를 요구할지 여부
     * - 콘텐츠 인가 매니저의 분리 여부와 DM 인가 규칙을 함께 적용할 방식
     *
     * 정책 반영 시 콘텐츠 시청·채팅과 DM의 기존 동작을 함께 회귀 검증한다.
     */
    @Bean
    public AuthorizationManager<Message<?>> webSocketAuthorizationManager(
            @Qualifier("directMessageAuthorizationManager")
            AuthorizationManager<MessageAuthorizationContext<?>> directMessageAuthorizationManager
    ){
        // 먼저 일치한 규칙을 적용한다. CONNECT의 permitAll은 앞선 인터셉터의 JWT 검증을 생략하지 않는다.
        return MessageMatcherDelegatingAuthorizationManager.builder()
                .simpTypeMatchers(
                        SimpMessageType.CONNECT,
                        SimpMessageType.HEARTBEAT,
                        SimpMessageType.UNSUBSCRIBE,
                        SimpMessageType.DISCONNECT
                ).permitAll()
                // DM의 정상 전송·구독 두 경로만 참여 검증으로 연결한다.
                .simpMessageDestMatchers(
                        "/pub/conversations/{conversationId}/direct-messages"
                ).access(directMessageAuthorizationManager)
                .simpSubscribeDestMatchers(
                        "/sub/conversations/{conversationId}/direct-messages"
                ).access(directMessageAuthorizationManager)
                // DM 접두사 안의 다른 경로·방향은 거절한다. /sub/** 전체의 SEND를 차단하는 규칙은 아니다.
                .simpDestMatchers(
                        "/pub/conversations/**",
                        "/sub/conversations/**"
                ).denyAll()
                // 콘텐츠의 기존 구독 흐름은 유지한다. 콘텐츠 전용 제한은 위 협의 항목에 따라 별도 적용한다.
                .simpSubscribeDestMatchers(
                        "/sub/contents/**"
                ).permitAll()
                // 위에서 허용한 구독 외에는 /sub/** 같은 광범위 구독도 거절한다.
                .simpTypeMatchers(SimpMessageType.SUBSCRIBE)
                .denyAll()
                .anyMessage().permitAll()
                .build();
    }
}
