package com.codeit.sb13.playlist_for_everyone.global.service;

/**
 * 데이터를 레디스 구독으로 전달하기 위한 객체.
 * Destination(url) 을 통해 데이터를 알맞는 서비스로 전달해야 한다.
 * topic 은 대용량 트래픽을 적절하게 분산하기 위해 구독을 관리하는 용도이다.
 * @param destination 데이터 전달 url
 * @param data 전달할 데이터(json)
 */

public record RedisMessageDto<T>(
        String destination,
        T data
) {}
