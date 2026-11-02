package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

public interface RuleInterface<T,R> {
    R run(T source);
    boolean match(T source);
}
