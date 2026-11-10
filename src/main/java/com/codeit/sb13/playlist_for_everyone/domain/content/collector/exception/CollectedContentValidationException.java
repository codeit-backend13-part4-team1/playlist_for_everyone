package com.codeit.sb13.playlist_for_everyone.domain.content.collector.exception;

public class CollectedContentValidationException
        extends IllegalArgumentException {

    public CollectedContentValidationException(String message) {
        super(message);
    }
}