package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.ViewingSessionInfoDto;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WebSocketUtils.check;

/**
 * 세션 - 구독  과 컨텐츠 간 관계 를 레디스에 저장하는 클래스
 * 내부적으론, Hash 와 ZSet 구조를 사용한다.
 * Key [Session] -> Hash[String subscription, String content]]
 * ZSet [Content] -> [Time expiredAt, String session:subscription]
 * ZSet [User] -> Content
 * [Content] -> counter
 */

@Slf4j
@Component
public class ViewingSessionRedisRepository {

    private final Duration TTL = Duration.ofSeconds(TTL_TIMEOUT);
    private final StringRedisTemplate redis;

    private final RedisScript<Long> registerScript;
    private final RedisScript<Long> unregisterScript;
    private final RedisScript<Long> refreshScript;
    private final RedisScript<Long> countScript;
    private final RedisScript<Long> deleteFromContentScript;
    private final RedisScript<List> countMultipleContentsScript;
    private final RedisScript<Long> unregisterAllScript;
    private final RedisScript<List> watcherCurrentViewerCountScript;

    @RequiredArgsConstructor
    static public class SetKey{
        private final String session;
        private final String subscription;

        @Override
        public String toString(){
            return session + DELIMITER + subscription;
        }
    }

    public ViewingSessionRedisRepository(
            StringRedisTemplate redis,
            @Qualifier("lua-script-register")
            RedisScript<Long> registerScript,
            @Qualifier("lua-script-unregister")
            RedisScript<Long> unregisterScript,
            @Qualifier("lua-script-refresh")
            RedisScript<Long> refreshScript,
            @Qualifier("lua-script-count")
            RedisScript<Long> countScript,
            @Qualifier("lua-script-delete-with-contentId")
            RedisScript<Long> deleteFromContentScript,
            @Qualifier("lua-count-multiple-contents")
            RedisScript<List> countMultipleContents,
            @Qualifier("lua-script-unregister-all")
            RedisScript<Long> unregisterAllScript,
            @Qualifier("lua-script-query-current-viewer")
            RedisScript<List> watcherCurrentViewerCountScript
    ){
        this.redis = redis;
        this.registerScript = registerScript;
        this.unregisterScript = unregisterScript;
        this.refreshScript = refreshScript;
        this.countScript = countScript;
        this.deleteFromContentScript = deleteFromContentScript;
        this.countMultipleContentsScript = countMultipleContents;
        this.unregisterAllScript = unregisterAllScript;
        this.watcherCurrentViewerCountScript = watcherCurrentViewerCountScript;
    }

    /*
    새션 Id 가 겹치는 건, hostname 별로 분간하거나 커넥션 별로 분간 해야 하므로 일단
    모든 세션 id 는 충돌되지 않는다는 가정하에 구현
    분산 인스턴스 간의 충돌 역시 고려하지 않고 구현한다.
     */

    /*
     * Redis 관련 예외
     * - IllegalStateException.class
     * - RedisConnectionFailureException.class
     * - QueryTimeoutException.class
     */

    /**
     * 시청 세션 등록 매서드.
     * 시청 세션을 등록하며, 유저별 첫 시청 세션 참여인지 여부를 반환한다.
     * @param subscriptionKey 구독 id.
     * @param contentKey 컨텐츠 id.
     * @param userKey 유저 id.
     * @param ctime 구독 생성 시간.
     */
    @Retryable(
            retryFor = {
                    RedisCommandExecutionException.class,
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 3)
    )
    public boolean register(
            UUID subscriptionKey,
            UUID contentKey,
            UUID userKey,
            Instant ctime
    ){
        // NPE 검사.
        validate(subscriptionKey, contentKey, userKey);

        /**
         * Key - sessionUUID, contentId, userId
         * Argv - sessionKeyPrefix, contentKeyPrefix, userKeyPrefix, delimiter, ttl, ctime
         * 인자를 주입해야 한다.
         * return 0 -> 이미 구독한 사용자, 1 -> 신규 사용자, 기타 -> error(RedisCommandExecutionException)
         */
        Long res = redis.execute(registerScript,
                List.of(subscriptionKey.toString(), contentKey.toString(), userKey.toString()),
                VIEW_SESSION_KEY_PREFIX,
                VIEW_CONTENT_KEY_PREFIX,
                VIEW_USER_KEY_PREFIX,
                DELIMITER,
                String.valueOf(TTL.toMillis()),
                ctime.toString()
        );

        return res.equals(1L);
    }


    /**
     * 세션 등록 취소 매서드
     * @param subscriptionKey 세션 key
     */
    @Retryable(
            retryFor = {
                    RedisCommandExecutionException.class,
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 3)
    )
    public void unregister(
            UUID subscriptionKey
    ){
        validate(subscriptionKey);

        /**
         * Key - subscriptionKey
         * Argv - contentKeyPrefix, userKeyPrefix, sessionKeyPrefix, counterPrefix, delimiter
         * return 0 or Error
         */
        redis.execute(unregisterScript,
                List.of(subscriptionKey.toString()),
                VIEW_SESSION_KEY_PREFIX,
                VIEW_CONTENT_KEY_PREFIX,
                VIEW_USER_KEY_PREFIX,
                DELIMITER
        );
    }


    /**
     * 여러 구독들에 대한 등록 취소 매서드
     * @param subscriptionKeys 구독 id 목록
     */
    @Retryable(
            retryFor = {
                    RedisCommandExecutionException.class,
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 3)
    )
    public void unregisterAll(
            List<UUID> subscriptionKeys
    ){
        //  인자 요소 리스트
        List<String> args = new ArrayList<>(List.of(
                VIEW_SESSION_KEY_PREFIX,
                VIEW_CONTENT_KEY_PREFIX,
                VIEW_USER_KEY_PREFIX,
                DELIMITER
        ));
        args.addAll(
                subscriptionKeys.stream().map(UUID::toString).toList()
        );

        /**
         * Keys - {}
         * ARGV - contentKeyPrefix, userKeyPrefix, sessionKeyPrefix, counterPrefix, delimiter
         * return 0
         */
        redis.execute(unregisterAllScript, List.of(), args.toArray());

    }

    // ttl 리프레시 매서드
    @Retryable(
            retryFor = {
                    RedisCommandExecutionException.class,
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void refresh(List<UUID> subscriptionKeys){

        //  인자 요소 리스트
        List<String> args = new ArrayList<>(List.of(
                VIEW_SESSION_KEY_PREFIX,
                VIEW_CONTENT_KEY_PREFIX,
                VIEW_USER_KEY_PREFIX,
                DELIMITER,
                String.valueOf(TTL.toMillis())
        ));
        args.addAll(
                subscriptionKeys.stream().map(UUID::toString).toList()
        );

        /**
         * Keys - sessionId
         * ARGV - contentKeyPrefix, userKeyPrefix, sessionKeyPrefix, counterPrefix, delimiter, ttl
         * return 0
         */
        redis.execute(refreshScript,
                List.of(),
                args.toArray()
        );
    }

    @Retryable(
            retryFor = {
                    RedisCommandExecutionException.class,
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void deleteWithContentId(UUID contentId){

        /**
         * Keys - {}
         * ARGV - contentKeyPrefix, userKeyPrefix, sessionKeyPrefix, counterPrefix, delimiter
         * return 0
         */
        redis.execute(
                deleteFromContentScript,
                List.of(contentId.toString()),
                VIEW_SESSION_KEY_PREFIX,
                VIEW_CONTENT_KEY_PREFIX,
                VIEW_USER_KEY_PREFIX,
                DELIMITER
        );
    }


    /**
     * content 를 시청중인 유효 시청자 수 반환.
     * 특정 유저의 중복 시청은 1명으로 친다.
     * @param contentId
     * @return
     */
    public Long getWatcherCount(String contentId){
        validate(contentId);

        return redis.execute(countScript,
                List.of(contentId),
                VIEW_CONTENT_KEY_PREFIX
        );
    }

    /**
     * 컨텐츠 별 시청자 수를 map 으로 반환.
     * List 에 null 이 올 경우, NullPointException 반환
     * @param contents contentId 리스트
     * @return contentId 가 비어있을 경우, 빈 map 반환.
     */
    @Retryable(
            retryFor = {
                    RedisCommandExecutionException.class,
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public Map<UUID, Long> getWatcherCountWithMultipleContents(List<String> contents){
        Objects.requireNonNull(contents);

        if(contents.isEmpty()) return Map.of();

        List<String> args = new ArrayList<>();
        args.add(VIEW_CONTENT_KEY_PREFIX);
        args.addAll(contents);
        List<?> query = redis.execute(countMultipleContentsScript,
                List.of(),
                args.toArray()
        );

        if (query == null || query.size() != contents.size()) {
            throw new RedisCommandExecutionException("redis 조회 컬럼이 요청된 컨텐츠 컬럼과 일치하지 않습니다.");
        }

        return query.stream().map(item -> (List<?>) item)
                .collect(Collectors.toMap(
                        item -> UUID.fromString(item.get(0).toString()),
                        item -> (Long) item.get(1),
                        (existing, replacement) -> existing // 중복 제거용 세 번째 인자.
                ));
    }


    /**
     * 컨텐츠에 대한 현재 시청자 id 리스트를 List 로 반환.
     * @param contentId 조회 대상용 유저 리스트
     */
    @Retryable(
            retryFor = {
                    RedisCommandExecutionException.class,
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public List<ViewingSessionInfoDto> currentViewerIdList(String contentId){
        List<?> query = redis.execute(
                watcherCurrentViewerCountScript,
                List.of(contentId),
                VIEW_SESSION_KEY_PREFIX,
                VIEW_CONTENT_KEY_PREFIX,
                VIEW_USER_KEY_PREFIX,
                DELIMITER
        );

        return query.stream().map(item -> {
            List<?> field =  (List<?>) item;
            return new ViewingSessionInfoDto(
                    UUID.fromString(field.get(0).toString()),
                    Instant.parse(field.get(1).toString()),
                    UUID.fromString(field.get(3).toString()),
                    UUID.fromString(field.get(2).toString())
            );
        }).toList();
    }


    /*
     * RECOVER 매서드
     */

    @Recover
    public void failRecover(RuntimeException e) {
        logRetryFailure(e);
        throw e;
    }

    @Recover
    public Map<UUID, Long> failMapRecover(
            RuntimeException e,
            List<String> contents
    ) {
        logRetryFailure(e);
        throw e;
    }

    private void logRetryFailure(RuntimeException e) {
        RetryContext context =
                RetrySynchronizationManager.getContext();

        log.error(
                "[{}] Redis 작업 최종 실패. attempts={}",
                getClass().getSimpleName(),
                context != null ? context.getRetryCount() : -1,
                e
        );
    }


    /*
     * private 편의 매서드
     */

    // 커스텀 예외를 작성한다면 여기에.
    private void validate(Object ...args){
        for (Object arg : args){
            check(arg);
        }
    }

}
