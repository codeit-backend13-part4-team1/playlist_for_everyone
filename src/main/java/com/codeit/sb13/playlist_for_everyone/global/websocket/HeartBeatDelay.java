package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.global.service.lock.SpreadLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 로직이 실핸되는 구간에 딜레이를 걸어서 반복요청에 의한 연산 부하를 방지하기 위한 클래스.
 * 기본적으로 키를 기반으로, 해당 요청의 인터벌을 측정해서 진행 가능 여부를 반환하도록 한다.
 */

@Slf4j
@Component
public class HeartBeatDelay {
    private final ConcurrentHashMap<Object, Instant> keys = new ConcurrentHashMap<>();
    private final AtomicBoolean autoRefresh = new AtomicBoolean(false);
    private final SpreadLock locks;

    // 기본 싱글톤이라 문제는 없겠지만, 여러 스레드에서 동시에 접근할 수 있으므로 volatile 키워드를 추가한다.
    private final Long interval;

    public HeartBeatDelay(
            @Value("${heartBeatDelay.interval:0}") Long interval,
            SpreadLock locks
    ) {
        this.interval = interval;
        this.locks = locks;
    }

    private void update(Object key){
        keys.put(key, Instant.now());
    }

    private boolean checkInterval(Object key){
        Instant now = Instant.now();
        Instant past = keys.get(key);

        if (past == null) return true;

        return Duration.between(past, now)
                .compareTo(Duration.ofMillis(interval / 2)) > 0;
    }

    // 1분마다 오개된 세션 갱신.
    private void chronRefresh(){
        // interval 이 0 이거나 없는 텀이라면 자동갱신 안함.
        if (interval == null || interval <= 0) return;

        // 이미 실행중이라면 실행 안함.
        if (!autoRefresh.compareAndSet(false,true)) return;

        Runnable r = () -> {
            int count = 0;
            log.info("[{}] 딜레이 {} 밀리초 텀 갱신 시작",this.getClass().getSimpleName(),interval);
            try{
                while (interval > 0) {
                    try {
                        Instant deleteLine = Instant.now().minusMillis(interval);
                        keys.forEach((key,time) -> {
                           if (time.isBefore(deleteLine)) {
                               keys.remove(key,time);   // time 이 조회할 때와 같다면 삭제.
                           }
                        });

                        if (count > 0) count = 0;

                        Thread.sleep(interval);
                    } catch (RuntimeException e) {
                        if (count < 3) {
                            log.warn("[{}] 딜레이 자료구조 갱신 실패",this.getClass().getSimpleName());
                            count ++;
                        } else break;
                        Thread.sleep(interval);
                    }
                }
            } catch (InterruptedException e) {
                log.error("[{}] 딜레이 자료구조 갱신 실패",this.getClass().getSimpleName(),e);
                Thread.currentThread().interrupt();
            } finally {
                autoRefresh.compareAndSet(true,false);
            }
        };

        // 주기적 정리는 새로운 작업 스레드로 호출.
        Thread t = new Thread(r);
        t.setDaemon(true);
        try{
            t.start();
        } catch (Exception e) {
            log.error("[{}] 딜레이 갱신 스레드 기동 실패",this.getClass().getSimpleName(),e);
            autoRefresh.set(false);
        }
    }

    /**
     * 키를 기반으로 해당 로직이 충분한 텀을 두고 실행되었는지 여부를 반환하는 매서드.
     * @param key 인터벌 측정에 사용될 키.
     * @return 이후 로직 진행 가능 여부. key 가 처음 등록 되었다면, true 를 반환한다.
     */
    public boolean check(Object key){
        // 인터벌 설정이 없다면, 딜레이 안 걸고, 저장 안 한다.
        if (interval == null || interval <= 0) return true;

        synchronized (locks.lock(key)){
            boolean res = checkInterval(key);
            if (res) update(key);
            return res;
        }
    }


}