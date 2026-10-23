package com.codeit.sb13.playlist_for_everyone.domain.content.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContentType {

    movie("영화"),
    tvSeries("TV 시리즈"),
    sports("스포츠");

    private final String description;

}