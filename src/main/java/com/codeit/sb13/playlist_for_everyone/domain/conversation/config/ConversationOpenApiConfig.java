package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 응답 DTO에 문서 전용 어노테이션을 추가하지 않고 대화방의 OpenAPI 스키마를 보완한다.
 *
 * <p>실제 JSON 응답을 바꾸는 설정은 아니다. 목록의 반환형에서 추론한 제네릭 스키마를
 * application/json 응답에 연결하고, ConversationDto의 lastestMessage에는 메시지 객체 또는
 * null을 허용한다. 공용 CursorResponse와 MessageDto의 정의는 유지한다.
 */
@Configuration
public class ConversationOpenApiConfig {

    @Bean
    public OpenApiCustomizer conversationOpenApiCustomizer() {
        return openApi -> {
            configureSearchResponse(openApi);

            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }

            Schema<?> conversationSchema = openApi.getComponents().getSchemas().get("ConversationDto");
            if (conversationSchema == null || conversationSchema.getProperties() == null) {
                return;
            }

            Schema<?> latestMessageSchema = conversationSchema.getProperties().get("lastestMessage");
            // 직접 참조하는 속성만 보완해 이미 anyOf로 변환된 스키마를 다시 감싸지 않는다.
            if (latestMessageSchema == null || latestMessageSchema.get$ref() == null) {
                return;
            }

            // OpenAPI 3.1은 null을 별도 타입으로 표현한다. 공용 MessageDto 스키마는 유지한다.
            JsonSchema nullSchema = new JsonSchema();
            nullSchema.addType("null");

            JsonSchema nullableMessageSchema = new JsonSchema();
            nullableMessageSchema.setAnyOf(List.of(latestMessageSchema, nullSchema));
            conversationSchema.addProperty("lastestMessage", nullableMessageSchema);
        };
    }

    private void configureSearchResponse(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        var path = openApi.getPaths().get("/api/conversations");
        if (path == null || path.getGet() == null || path.getGet().getResponses() == null) {
            return;
        }

        var response = path.getGet().getResponses().get("200");
        if (response == null || response.getContent() == null) {
            return;
        }

        var content = response.getContent();
        var json = content.get("application/json");
        var inferred = content.get("*/*");
        // useReturnTypeSchema로 추론한 스키마가 */*에 추가되면, 선언한 JSON 응답으로 옮긴다.
        if (json != null && json.getSchema() == null && inferred != null && inferred.getSchema() != null) {
            json.setSchema(inferred.getSchema());
            content.remove("*/*");
        }
    }
}
