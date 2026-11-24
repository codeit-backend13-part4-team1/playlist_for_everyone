package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Optional;

public class EcsTaskMetadataClient {

    private final RestClient restClient;

    public EcsTaskMetadataClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 현재 앱을 실행하는 ECS 태스크 조회 */
    public Optional<CollectionExecutionOwner> getCurrentOwner(
            String metadataUri
    ) {
        if (!StringUtils.hasText(metadataUri)) {
            return Optional.empty();
        }

        String baseUri = metadataUri.strip();

        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }

        var response = restClient.get()
                .uri(baseUri + "/task")
                .retrieve()
                .body(TaskMetadata.class);

        if (response == null
                || !StringUtils.hasText(response.cluster())
                || !StringUtils.hasText(response.taskArn())) {
            throw new IllegalStateException(
                    "ECS 실행 태스크 정보를 확인할 수 없습니다."
            );
        }

        return Optional.of(new CollectionExecutionOwner(
                response.cluster(),
                response.taskArn()
        ));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TaskMetadata(
            @JsonProperty("Cluster") String cluster,
            @JsonProperty("TaskARN") String taskArn
    ) {
    }
}