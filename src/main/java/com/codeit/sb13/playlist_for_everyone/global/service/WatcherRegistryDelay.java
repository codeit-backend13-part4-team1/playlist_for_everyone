package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.WatcherScriptConfig.TTL_TIMEOUT;

@Component
@RequiredArgsConstructor
public class WatcherRegistryDelay {

    private final ConcurrentHashMap<String, Instant> sessionList = new ConcurrentHashMap<>();

    public boolean checkRefresh(String session){

        if (session == null) return false;

        boolean res = allowRefresh(session);
        // 세션 정보가 없다면 초기 시도시에 시간 체크
        sessionList.computeIfAbsent(session, k -> Instant.now());
        return res;
    }

    public void updateRefreshTime(String session){
        sessionList.put(session, Instant.now());
    }

    private boolean allowRefresh(String session){
        Instant now = Instant.now();
        Instant sessionSavedTime = sessionList.get(session);

        if (sessionSavedTime == null) return true;

        return Duration.between(sessionSavedTime, now)
                .compareTo(Duration.ofSeconds(TTL_TIMEOUT / 2)) > 0;
    }

    // 1분마다 오개된 세션 갱신.
    @Scheduled(fixedDelay = 60000)
    private void refreshSessionList(){
        Instant now = Instant.now();
        for (String key : sessionList.keySet()){
            Instant sessionRefreshTime = sessionList.get(key);

            // 현재 시간보다 TTL 이상 과거의 리프레시 삭제.
            if (now.minusSeconds(TTL_TIMEOUT).isAfter(sessionRefreshTime)){
                sessionList.remove(key);
            }
        }
    }

}
