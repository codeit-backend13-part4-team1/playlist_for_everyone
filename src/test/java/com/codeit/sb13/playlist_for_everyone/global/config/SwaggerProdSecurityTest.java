package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SwaggerProdSecurityTest.DocumentController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class,
        SwaggerProdSecurityTest.DocumentController.class
})
// dev와 prod가 함께 활성화되어도 문서를 공개하지 않는지 검증한다.
// test는 테스트용 설정을 사용하기 위해 함께 활성화한다.
@ActiveProfiles({"dev", "prod", "test"})
@DisplayName("dev와 prod 동시 활성화 시 Swagger 접근 정책")
class SwaggerProdSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @ParameterizedTest
    @ValueSource(strings = {
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config"
    })
    @DisplayName("dev와 prod가 함께 활성화되면 문서의 비로그인 접근을 차단한다.")
    void dev와_prod가_함께_활성화되면_문서_접근에_인증이_필요하다(String path) throws Exception {
        // Authorization 헤더 없이 요청하므로 비로그인 요청이다.
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        // 토큰이 없는 요청에서 JWT 검증이나 사용자 조회는 수행하지 않는다.
        verifyNoInteractions(jwtTokenProvider, userService);

    }

    /**
     * 문서 경로가 존재하는 상황에서 보안 정책을 검증하기 위한 대역이다.
     * 실제 Swagger UI 렌더링이나 운영 DB 연결을 검증하는 데스트는 아니다.
     */

    @RestController
    public static class DocumentController {
        @GetMapping({
                "/swagger-ui.html",
                "/swagger-ui/index.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config"
        })
        public String document() {
            return "document";
        }
    }
}
