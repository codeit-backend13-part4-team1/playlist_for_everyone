package com.codeit.sb13.playlist_for_everyone.domain.follow.repository;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        showSql = false,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@Testcontainers
@DisplayName("팔로우 리포지토리")
class FollowRepositoryTest {

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
    private FollowRepository followRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("팔로우를 저장하고 재조회하면 UUID와 생성 시각 및 사용자 연결이 유지된다.")
    void saveAndFind_preservesIdCreatedAtAndUserReferences(){
        User follower = User.builder()
                .email("follower@test.example")
                .name("팔로우하는 사용자")
                .role(Role.USER)
                .build();

        User followee = User.builder()
                .email("followee@test.example")
                .name("팔로우받는 사용자")
                .role(Role.USER)
                .build();

        entityManager.persist(follower);
        entityManager.persist(followee);

        Follow saved = followRepository.saveAndFlush(new Follow(follower, followee));
        UUID followId = saved.getId();

        assertThat(followId).isNotNull();
        entityManager.clear();

        Follow found = followRepository.findById(followId).orElseThrow();

        assertThat(found.getId()).isEqualTo(followId);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getFollowerId()).isEqualTo(follower.getId());
        assertThat(found.getFolloweeId()).isEqualTo(followee.getId());

    }

}