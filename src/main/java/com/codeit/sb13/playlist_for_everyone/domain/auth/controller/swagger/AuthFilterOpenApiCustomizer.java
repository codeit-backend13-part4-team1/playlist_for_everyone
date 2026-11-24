package com.codeit.sb13.playlist_for_everyone.domain.auth.controller.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Spring Security 필터가 처리해 springdoc이 수집하지 못하는 로그인·로그아웃 API를 문서에 추가한다.
 */
@Component
public class AuthFilterOpenApiCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        // 그린에서 구현
    }
}
