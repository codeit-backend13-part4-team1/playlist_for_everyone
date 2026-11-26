package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("소셜 로그인 보안 체인 통합")
class SocialLoginSecurityIntegrationTest {

    private static final String SESSION_COOKIE = "OAUTH_SESSION";
    private static final String SESSION_NAMESPACE = "everyone:oauth:session";
    // 프록시 뒤에서도 요청 Host와 무관하게 설정된 서비스 주소로 이동해야 한다. (application-test.yaml의 redirect-base-url)
    private static final String FAILURE_URL = "https://everyone.test/#/sign-in?error=oauth_failed&error_message=";

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2.6-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", ()-> redis.getMappedPort(6379));
    }

    @MockitoBean
    private FileStorage fileStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys(SESSION_NAMESPACE + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("구글 로그인을 시작하면 고정된 콜백 주소로 구글 인증 화면에 이동한다.")
    void 구글_로그인을_시작하면_고정된_콜백_주소로_구글_인증_화면에_이동한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        MultiValueMap<String, String> params = queryParams(location);

        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(params.getFirst("client_id")).isEqualTo("test-google-client-id");
        // 요청 Host가 아니라 설정한 주소로 콜백을 받는다.
        assertThat(params.getFirst("redirect_uri"))
                .isEqualTo("https://everyone.test/login/oauth2/code/google");
        assertThat(params.getFirst("scope")).contains("openid", "profile", "email");
        assertThat(params.getFirst("state")).isNotBlank();
    }

    @Test
    @DisplayName("카카오 로그인을 시작하면 닉네임 동의만 요청한다.")
    void 카카오_로그인을_시작하면_닉네임_동의만_요청한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        MultiValueMap<String, String> params = queryParams(location);

        assertThat(location).startsWith("https://kauth.kakao.com/oauth/authorize");
        assertThat(params.getFirst("client_id")).isEqualTo("test-kakao-client-id");
        assertThat(params.getFirst("redirect_uri"))
                .isEqualTo("https://everyone.test/login/oauth2/code/kakao");
        assertThat(params.getFirst("scope")).isEqualTo("profile_nickname");
    }

    @Test
    @DisplayName("로그인 요청 정보는 HttpOnly 쿠키와 연결된 Redis 세션에 10분 동안 저장된다.")
    void 로그인_요청_정보는_HttpOnly_쿠키와_연결된_Redis_세션에_10분_동안_저장된다() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn();

        Cookie sessionCookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isTrue();

        Set<String> keys = redisTemplate.keys(SESSION_NAMESPACE + ":sessions:*");
        Long ttl = redisTemplate.getExpire(keys.iterator().next(), TimeUnit.SECONDS);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(600);
    }

    @Test
    @DisplayName("사용자가 동의 화면에서 취소하면 access_denied로 이동한다.")
    void 사용자가_동의_화면에서_취소하면_access_denied로_이동한다() throws Exception {
        MvcResult start = mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isFound())
                .andReturn();
        String state = queryParams(start.getResponse().getRedirectedUrl()).getFirst("state");
        Cookie sessionCookie = start.getResponse().getCookie(SESSION_COOKIE);

        mockMvc.perform(get("/login/oauth2/code/kakao")
                .cookie(sessionCookie)
                .param("error", "access_denied")
                .param("state", state))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(FAILURE_URL + "access_denied"));
    }

    @Test
    @DisplayName("로그인 요청 없이 콜백이 오면 login_failed로 이동한다.")
    void 로그인_요청_없이_콜백이_오면_login_failed로_이동한다() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "test-code")
                        .param("state", "unknown-state"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(FAILURE_URL + "login_failed"));
    }

    @Test
    @DisplayName("로그인을 시작한 브라우저가 아니면 같은 state로도 콜백을 거부한다.")
    void 로그인을_시작한_브라우저가_아니면_같은_state로도_콜백을_거부한다() throws Exception {
        MvcResult start = mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isFound())
                .andReturn();
        String state = queryParams(start.getResponse().getRedirectedUrl()).getFirst("state");

        // 세션 쿠키 없이 state만 맞춘 요청은 다른 브라우저에서 온 요청이다.
        mockMvc.perform(get("/login/oauth2/code/kakao")
                        .param("code", "attacker-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(FAILURE_URL + "login_failed"));
    }

    @Test
    @DisplayName("OAuth 경로가 아니면 OAuth 세션을 만들지 않는다.")
    void OAuth_경로가_아니면_OAuth_세션을_만들지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getCookie(SESSION_COOKIE)).isNull();
        assertThat(redisTemplate.keys(SESSION_NAMESPACE + ":*")).isEmpty();
    }

    // 인증 화면 주소의 쿼리 값을 디코딩해 비교한다.
    private static MultiValueMap<String, String> queryParams(String location) {
        MultiValueMap<String, String> encoded =
                UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        MultiValueMap<String, String> decoded = new LinkedMultiValueMap<>();
        encoded.forEach((key, values) -> values.forEach(value ->
                decoded.add(key, URLDecoder.decode(value, StandardCharsets.UTF_8))));
        return decoded;
    }
}
