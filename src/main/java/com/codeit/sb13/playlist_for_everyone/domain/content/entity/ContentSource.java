package com.codeit.sb13.playlist_for_everyone.domain.content.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContentSource {

    TMDB("TMDB"),
    THE_SPORTS_DB("TheSportsDB"),
    ADMIN("관리자 직접 등록");

    private final String description;

}