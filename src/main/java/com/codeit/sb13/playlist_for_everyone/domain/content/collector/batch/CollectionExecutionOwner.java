package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

public record CollectionExecutionOwner(
        String clusterArn,
        String taskArn
) {
}