package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
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

        // 체킹에 따른 리스트 갱신
        refreshSessionList();
        // 없다면 초기 시도 시각 세팅
        sessionList.computeIfAbsent(session, k -> Instant.now());

        return allowRefresh(session);
    }

    private boolean allowRefresh(String session){
        Instant now = Instant.now();
        Instant sessionSavedTime = sessionList.get(session);

        if (sessionSavedTime == null) return true;

        return Duration.between(sessionSavedTime, now)
                .compareTo(Duration.ofSeconds(TTL_TIMEOUT / 2)) > 0;
    }

    private void refreshSessionList(){
        Instant now = Instant.now();
        for (String key : sessionList.keySet()){
            Instant sessionRefreshTime = sessionList.get(key);

            // 현재 시간보다 TTL 이상 과거의 리프레시 삭제.
            if (sessionRefreshTime.isBefore(now.minusSeconds(TTL_TIMEOUT))){
                sessionList.remove(key);
            }
        }
    }

}
