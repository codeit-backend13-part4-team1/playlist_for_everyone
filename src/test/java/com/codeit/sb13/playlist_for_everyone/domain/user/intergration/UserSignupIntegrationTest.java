package com.codeit.sb13.playlist_for_everyone.domain.user.intergration;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.batch.job.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("PostgreSQL 회원가입 통합 테스트")
class UserSignupIntegrationTest {

    private static final String EMAIL = "signup@example.com";
    private static final String PASSWORD = "test-password";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("signup_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입하면 사용자의 정보와 암호화된 비밀번호를 DB에 저장한다")
    void 회원가입하면_사용자를_DB에_저장한다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value("테스트"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        String id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );

        User savedUser = userRepository.findById(UUID.fromString(id))
                .orElseThrow();

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
        assertThat(savedUser.getName()).isEqualTo("테스트");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.isLocked()).isFalse();

        assertThat(savedUser.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("이미 가입된 이메일로 다시 요청하면 400을 반환한다")
    void 이미_가입한_이메일이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("DuplicateEmailException"))
                .andExpect(jsonPath("$.details.email").value(EMAIL));

        assertThat(countUsersByEmail()).isEqualTo(1L);
    }

    @Test
    @DisplayName("동시 가입 시 한 건만 성공하고 DB 중복 오류를 400으로 변환한다")
    void 동시_가입하면_한_건만_성공한다() throws Exception {

        CyclicBarrier barrier = new CyclicBarrier(2);

        doAnswer(invocation -> {
            String encodedPassword = (String) invocation.callRealMethod();

            barrier.await(15, TimeUnit.SECONDS);

            return encodedPassword;
        }).when(passwordEncoder).encode(PASSWORD);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {

            Future<MvcResult> first = executor.submit(this::performSignup);
            Future<MvcResult> second = executor.submit(this::performSignup);

            List<MvcResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );


            List<Integer> statuses = results.stream()
                    .map(result -> result.getResponse().getStatus())
                    .toList();

            assertThat(statuses).containsExactlyInAnyOrder(201, 400);
            assertThat(countUsersByEmail()).isEqualTo(1L);

            MvcResult failedResult = results.stream()
                    .filter(result ->
                            result.getResponse().getStatus() == 400)
                    .findFirst()
                    .orElseThrow();

            assertThat(failedResult.getResolvedException())
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);

            String exceptionName = JsonPath.read(
                    failedResult.getResponse().getContentAsString(),
                    "$.exceptionName"
            );

            assertThat(exceptionName)
                    .isEqualTo("DuplicateEmailException");

        } finally {
            executor.shutdownNow();

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시 가입 테스트 작업이 종료되지 않았습니다."
                );
            }
        }
    }



    private MvcResult performSignup() throws Exception {
        return mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest()))
                .andReturn();
    }

    private long countUsersByEmail() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email =?",
                Long.class,
                EMAIL
        );
    }


    private String signupRequest() {
        return """
                {
                 "email": "%s",
                 "name": "테스트",
                 "password": "%s"
                }
                """.formatted(EMAIL, PASSWORD);
    }








}
