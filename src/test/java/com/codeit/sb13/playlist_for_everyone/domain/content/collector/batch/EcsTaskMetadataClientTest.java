package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EcsTaskMetadataClientTest {

    private MockRestServiceServer server;
    private EcsTaskMetadataClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();

        server = MockRestServiceServer.bindTo(builder).build();
        client = new EcsTaskMetadataClient(builder.build());
    }

    @Test
    void 현재_태스크의_클러스터와_ARN을_조회한다() {
        String metadataUri = "http://metadata.test/v4/current";

        server.expect(requestTo(metadataUri + "/task"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "Cluster": "arn:aws:ecs:ap-northeast-2:123456789012:cluster/test",
                          "TaskARN": "arn:aws:ecs:ap-northeast-2:123456789012:task/test/current",
                          "Family": "test-app",
                          "Containers": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var owner = client.getCurrentOwner(metadataUri);

        assertThat(owner).contains(new CollectionExecutionOwner(
                "arn:aws:ecs:ap-northeast-2:123456789012:cluster/test",
                "arn:aws:ecs:ap-northeast-2:123456789012:task/test/current"
        ));

        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 메타데이터_주소가_없으면_조회하지_않는다(String metadataUri) {
        assertThat(client.getCurrentOwner(metadataUri)).isEmpty();

        server.verify();
    }
}