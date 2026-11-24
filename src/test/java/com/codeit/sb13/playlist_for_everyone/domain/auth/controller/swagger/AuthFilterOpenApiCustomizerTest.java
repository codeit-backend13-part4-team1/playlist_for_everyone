package com.codeit.sb13.playlist_for_everyone.domain.auth.controller.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("필터가 처리하는 인증 API 문서")
class AuthFilterOpenApiCustomizerTest {

    private static final String SIGN_IN = "/api/auth/sign-in";
    private static final String SIGN_OUT = "/api/auth/sign-out";

    private final AuthFilterOpenApiCustomizer customizer = new AuthFilterOpenApiCustomizer();
    private OpenAPI openApi;

    @BeforeEach
    void setUp() {
        // springdoc이 컨트롤러를 읽은 뒤의 상태처럼 빈 경로·컴포넌트를 둔다.
        openApi = new OpenAPI().paths(new Paths()).components(new Components());
    }

    @Test
    @DisplayName("로그인을 인증 태그의 signIn 작업으로 추가한다.")
    void 로그인을_인증_태그의_signIn_작업으로_추가한다() {
        customizer.customise(openApi);

        Operation signIn = postOf(SIGN_IN);
        assertThat(signIn.getOperationId()).isEqualTo("signIn");
        assertThat(signIn.getTags()).containsExactly("인증");
    }

    @Test
    @DisplayName("로그인 요청은 form 형식의 username과 password를 필수로 받는다.")
    void 로그인_요청은_form_형식의_username과_password를_필수로_받는다() {
        customizer.customise(openApi);

        Schema<?> body = postOf(SIGN_IN).getRequestBody().getContent()
                .get(MediaType.APPLICATION_FORM_URLENCODED_VALUE).getSchema();
        assertThat(body.getProperties()).containsOnlyKeys("username", "password");
        assertThat(body.getRequired()).containsExactlyInAnyOrder("username", "password");
    }

    @Test
    @DisplayName("로그인 성공은 JwtDto, 실패는 공통 에러 응답으로 문서화한다.")
    void 로그인_성공은_JwtDto_실패는_공통_에러_응답으로_문서화한다() {
        customizer.customise(openApi);

        ApiResponses responses = postOf(SIGN_IN).getResponses();
        assertThat(responses).containsKeys("200", "401", "403", "500");
        assertThat(schemaRef(responses.get("200"))).isEqualTo("#/components/schemas/JwtDto");
        assertThat(schemaRef(responses.get("401"))).isEqualTo("#/components/schemas/ApiErrorResponse");
    }

    @Test
    @DisplayName("로그아웃을 본문 없는 signOut 작업으로 추가한다.")
    void 로그아웃을_본문_없는_signOut_작업으로_추가한다() {
        customizer.customise(openApi);

        Operation signOut = postOf(SIGN_OUT);
        assertThat(signOut.getOperationId()).isEqualTo("signOut");
        assertThat(signOut.getTags()).containsExactly("인증");
        assertThat(signOut.getRequestBody()).isNull();
        assertThat(signOut.getResponses()).containsKeys("204", "403", "500");
    }

    @Test
    @DisplayName("응답에 쓰는 스키마가 없으면 함께 등록한다.")
    void 응답에_쓰는_스키마가_없으면_함께_등록한다() {
        customizer.customise(openApi);

        // JwtDto 안의 UserDto까지 등록돼야 문서에서 펼쳐 볼 수 있다.
        assertThat(openApi.getComponents().getSchemas())
                .containsKeys("JwtDto", "UserDto", "ApiErrorResponse");
    }

    @Test
    @DisplayName("springdoc이 이미 만든 스키마는 덮어쓰지 않는다.")
    void springdoc이_이미_만든_스키마는_덮어쓰지_않는다() {
        Schema<?> existing = new ObjectSchema().description("springdoc이 만든 스키마");
        openApi.getComponents().addSchemas("JwtDto", existing);

        customizer.customise(openApi);

        assertThat(openApi.getComponents().getSchemas().get("JwtDto")).isSameAs(existing);
    }

    @Test
    @DisplayName("로그인과 로그아웃은 Bearer 인증을 요구하지 않는다.")
    void 로그인과_로그아웃은_Bearer_인증을_요구하지_않는다() {
        customizer.customise(openApi);

        assertThat(postOf(SIGN_IN).getSecurity()).isNullOrEmpty();
        assertThat(postOf(SIGN_OUT).getSecurity()).isNullOrEmpty();
    }

    private Operation postOf(String path) {
        assertThat(openApi.getPaths()).containsKey(path);
        return openApi.getPaths().get(path).getPost();
    }

    private String schemaRef(ApiResponse response) {
        return response.getContent().get(MediaType.APPLICATION_JSON_VALUE).getSchema().get$ref();
    }

}