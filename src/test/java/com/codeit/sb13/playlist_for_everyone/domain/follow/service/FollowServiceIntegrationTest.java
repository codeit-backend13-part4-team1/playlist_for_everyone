package com.codeit.sb13.playlist_for_everyone.domain.follow.service;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("팔로우 서비스 통합 테스트")
public class FollowServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID followerId = UUID.randomUUID();
    private final UUID followeeId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        insertUser(followerId, "팔로우하는 사용자");
        insertUser(followeeId, "팔로우받는 사용자");
    }

    @AfterEach
    void cleanUp(){
        followRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", followerId, followeeId);
    }

    @Test
    @DisplayName("같은 팔로우를 다시 요청하면 기존 ID와 생성 시각을 유지하고 한건만 저장한다.")
    void follow_returnsExistingRelationForDuplicateRequest(){
        FollowCommand command = new FollowCommand(followeeId, followerId);

        FollowResult first = followService.follow(command);

        Follow before = followRepository.findById(first.followDto().id()).orElseThrow();
        FollowResult second = followService.follow(command);
        Follow after = followRepository.findById(second.followDto().id()).orElseThrow();

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.followDto()).isEqualTo(first.followDto());

        assertThat(before.getCreatedAt()).isNotNull();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());

        assertThat(followRepository.count()).isEqualTo(1L);

    }

    private void insertUser(UUID userId, String name){
        jdbcTemplate.update(
                """
                    INSERT INTO users (id, email, nickname, role, enabled, created_at)
                    VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                    """,
                userId,
                userId+"@test.example",
                name
        );
    }

}
