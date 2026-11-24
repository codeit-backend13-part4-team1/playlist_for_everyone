package com.codeit.sb13.playlist_for_everyone.domain.auth.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Security 필터가 처리해 springdoc이 수집하지 못하는 로그인·로그아웃·소셜 로그인 시작 API를 문서에 추가한다.
 *
 * <p>프로토타입 문서와 같이 "인증" 태그 안에 두고 operationId를 signIn, signOut으로 맞춘다.
 * 두 API는 로그인 전에도 호출하므로 bearerAuth를 붙이지 않는다.
 * 응답이 참조하는 스키마가 문서에 없으면 함께 등록하고, springdoc이 이미 만든 스키마는 그대로 둔다.
 */
@Component
public class AuthFilterOpenApiCustomizer implements OpenApiCustomizer {

    static final String TAG = "인증";
    static final String SIGN_IN_PATH = "/api/auth/sign-in";
    static final String SIGN_OUT_PATH = "/api/auth/sign-out";
    static final String SOCIAL_LOGIN_PATH = "/oauth2/authorization/{provider}";

    private static final String FORM = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
    private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
    private static final String ERROR_SCHEMA = "ApiErrorResponse";

    @Override
    public void customise(OpenAPI openApi) {
        registerSchemas(openApi, JwtDto.class, ApiErrorResponse.class);
        openApi.path(SIGN_IN_PATH, new PathItem().post(signIn()));
        openApi.path(SIGN_OUT_PATH, new PathItem().post(signOut()));
        openApi.path(SOCIAL_LOGIN_PATH, new PathItem().get(socialLogin()));
    }

    private Operation signIn() {
        Schema<?> form = new ObjectSchema()
                .addProperty("username", new StringSchema().description("이메일"))
                .addProperty("password", new StringSchema().format("password").description("비밀번호"));
        form.setRequired(List.of("username", "password"));

        return new Operation()
                .operationId("signIn")
                .addTagsItem(TAG)
                .summary("로그인")
                .description("이메일·비밀번호(form)로 로그인합니다. Access Token은 본문, Refresh Token은 쿠키로 발급합니다.")
                .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content().addMediaType(FORM, new MediaType().schema(form))))
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse("로그인 성공", "JwtDto"))
                        .addApiResponse("401", jsonResponse("로그인 실패", ERROR_SCHEMA))
                        .addApiResponse("403", jsonResponse("CSRF 오류", ERROR_SCHEMA))
                        .addApiResponse("500", new ApiResponse().description("서버 오류")));
    }

    private Operation signOut() {
        return new Operation()
                .operationId("signOut")
                .addTagsItem(TAG)
                .summary("로그아웃")
                .description("Refresh Token을 폐기하고 쿠키를 만료합니다.")
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("로그아웃 성공"))
                        .addApiResponse("403", jsonResponse("CSRF 오류", ERROR_SCHEMA))
                        .addApiResponse("500", jsonResponse("서버 오류", ERROR_SCHEMA)));
    }

    private Operation socialLogin() {
        StringSchema providers = new StringSchema();
        providers.setEnum(List.of("google", "kakao"));

        return new Operation()
                .operationId("socialLogin")
                .addTagsItem(TAG)
                .summary("소셜 로그인 시작")
                .description("브라우저 주소 이동으로 호출하며 Swagger에서는 실행할 수 없습니다. 성공 후 /api/auth/refresh로 Access Token을 받습니다.")
                .addParametersItem(new PathParameter()
                        .name("provider")
                        .description("소셜 로그인 제공자")
                        .schema(providers))
                .responses(new ApiResponses()
                        .addApiResponse("302", new ApiResponse().description("제공자 인증 화면으로 이동")));
    }

    private ApiResponse jsonResponse(String description, String schemaName) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(JSON,
                        new MediaType().schema(new Schema<>().$ref(SCHEMA_REF_PREFIX + schemaName))));
    }

    /**
     * 응답이 참조하는 스키마와 그 안에서 쓰는 스키마(JwtDto 안의 UserDto 등)를 등록한다.
     * 같은 이름이 이미 있으면 springdoc이 컨트롤러에서 만든 것이라 덮어쓰지 않는다.
     */
    private void registerSchemas(OpenAPI openApi, Class<?>... types) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Components components = openApi.getComponents();

        for (Class<?> type : types) {
            ModelConverters.getInstance().readAll(type).forEach((name, schema) -> {
                if (components.getSchemas() == null || !components.getSchemas().containsKey(name)) {
                    components.addSchemas(name, schema);
                }
            });
        }
    }
}
