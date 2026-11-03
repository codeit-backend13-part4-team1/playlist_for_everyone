package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

public interface MatcherInterface<T,U> {
    void add(T rule);
    void run(U key);
}
