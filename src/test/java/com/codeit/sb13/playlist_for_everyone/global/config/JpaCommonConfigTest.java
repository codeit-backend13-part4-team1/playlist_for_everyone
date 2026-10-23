package com.codeit.sb13.playlist_for_everyone.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.testsupport.persistence.AuditedTestEntity;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.AuditingHandler;

@DataJpaTest(
    showSql = false,
    properties = "spring.data.jpa.repositories.enabled=false"
)
@EntityScan(basePackageClasses = AuditedTestEntity.class)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DisplayName("JPA Auditing 및 Querydsl 설정")
class JpaCommonConfigTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final LocalDateTime UPDATED_AT = CREATED_AT.plusMinutes(1);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JPAQueryFactory queryFactory;

    @Autowired
    @Qualifier("jpaAuditingHandler")
    private AuditingHandler auditingHandler;

    private final AtomicReference<LocalDateTime> now = new AtomicReference<>();

    @BeforeEach
    void useADeterministicAuditingClock() {
        now.set(CREATED_AT);
        auditingHandler.setDateTimeProvider(() -> Optional.of(now.get()));
    }

    @AfterEach
    void restoreAuditingClock() {
        auditingHandler.setDateTimeProvider(null);
    }

    @Test
    @DisplayName("엔티티 저장 시 UUID와 생성 시각 및 수정 시각을 자동으로 기록한다")
    void generatesUuidAndPersistsCreationAndModificationTimes() {
        AuditedTestEntity entity = new AuditedTestEntity("처음 저장");

        entityManager.persist(entity);
        entityManager.flush();
        UUID id = entity.getId();
        assertThat(id).isNotNull();
        entityManager.clear();
        AuditedTestEntity saved = entityManager.find(AuditedTestEntity.class, id);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(saved.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("엔티티 수정 시 ID와 생성 시각을 유지하고 수정 시각을 갱신한다")
    void updatesOnlyModificationTimeWhenAnEntityChanges() {
        AuditedTestEntity entity = new AuditedTestEntity("변경 전");
        entityManager.persist(entity);
        entityManager.flush();
        UUID id = entity.getId();
        entityManager.clear();
        now.set(UPDATED_AT);

        AuditedTestEntity saved = entityManager.find(AuditedTestEntity.class, id);
        saved.rename("변경 후");
        entityManager.flush();
        entityManager.clear();
        AuditedTestEntity updated = entityManager.find(AuditedTestEntity.class, id);

        assertThat(updated.getName()).isEqualTo("변경 후");
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(updated.getUpdatedAt()).isEqualTo(UPDATED_AT).isAfter(updated.getCreatedAt());
    }

    @Test
    @DisplayName("등록된 JPAQueryFactory로 조건에 맞는 엔티티만 조회한다")
    void queriesPersistedEntitiesWithTheConfiguredQueryFactory() {
        AuditedTestEntity target = new AuditedTestEntity("조회 대상");
        entityManager.persist(target);
        entityManager.persist(new AuditedTestEntity("다른 항목"));
        entityManager.flush();
        entityManager.clear();
        // 테스트 엔티티의 Q 클래스를 생성하지 않고 실제 Querydsl 쿼리를 실행한다.
        PathBuilder<AuditedTestEntity> entity = new PathBuilder<>(AuditedTestEntity.class, "auditedEntity");

        AuditedTestEntity found = queryFactory.selectFrom(entity)
                .where(entity.getString("name").eq("조회 대상"))
                .fetchOne();

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(target.getId());
        assertThat(found.getName()).isEqualTo("조회 대상");
        assertThat(queryFactory.selectFrom(entity)
                .where(entity.getString("name").eq("없는 항목")).fetch()).isEmpty();
    }
}
