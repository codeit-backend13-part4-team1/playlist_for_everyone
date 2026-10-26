package com.codeit.sb13.playlist_for_everyone.domain.follow.repository;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("팔로우를 삭제하면 관계만 제거되고 두 사용자 데이터는 유지된다.")
    void delete_removesFollowAndPreservesUsers(){
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
        UUID followerId = follower.getId();
        UUID followeeId = followee.getId();

        followRepository.delete(saved);
        followRepository.flush();
        entityManager.clear();

        assertThat(followRepository.findById(followId)).isEmpty();
        assertThat(entityManager.find(User.class, followerId)).isNotNull();
        assertThat(entityManager.find(User.class, followeeId)).isNotNull();
    }

    @Test
    @DisplayName("같은 방향의 팔로우를 중복저장하면 UNIQUE 제약조건 위반이 발생한다.")
    void save_rejectsDuplicateFollow(){
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

        followRepository.saveAndFlush(new Follow(follower, followee));

        assertThatThrownBy(()-> followRepository.saveAndFlush(new Follow(follower, followee)))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        e -> assertThat(e.getCause())
                                .isInstanceOfSatisfying(ConstraintViolationException.class,
                                cause-> {
                                    assertThat(cause.getConstraintName())
                                            .isEqualTo("uq_follows_follower_followee");
                                    assertThat(cause.getSQLState()).isEqualTo("23505");
                                })
                );

    }

    @Test
    @DisplayName("A에서 B로, B에서 A로의 팔로우를 각각 저장할 수 있다.")
    void save_allowsMutualFollow(){
        User userA = User.builder()
                .email("user-a@text.example")
                .name("사용자 A")
                .role(Role.USER)
                .build();

        User userB = User.builder()
                .email("user-b@text.example")
                .name("사용자 B")
                .role(Role.USER)
                .build();

        entityManager.persist(userA);
        entityManager.persist(userB);

        Follow aToB = followRepository.saveAndFlush(new Follow(userA, userB));
        Follow bToA = followRepository.saveAndFlush(new Follow(userB, userA));

        entityManager.clear();

        Follow foundAToB = followRepository.findByFolloweeIdAndFollowerId(userB.getId(), userA.getId())
                .orElseThrow();

        Follow foundBToA = followRepository.findByFolloweeIdAndFollowerId(userA.getId(), userB.getId())
                .orElseThrow();

        assertThat(followRepository.count()).isEqualTo(2);
        assertThat(foundAToB.getId()).isEqualTo(aToB.getId());
        assertThat(foundBToA.getId()).isEqualTo(bToA.getId());
        assertThat(foundAToB.getId()).isNotEqualTo(foundBToA.getId());

    }
}