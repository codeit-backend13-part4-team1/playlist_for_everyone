package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.time.LocalDateTime;
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
@Import({
        JpaAuditingConfig.class,
        QueryDslConfig.class
})
@Testcontainers
@DisplayName("다이렉트 메시지 리포지토리")
class DirectMessageRepositoryTest {

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
    private DirectMessageRepository directMessageRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final UUID LEGACY_USER_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LEGACY_USER_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LEGACY_CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LEGACY_MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final LocalDateTime LEGACY_CREATED_AT = LocalDateTime.of(2026, 11, 12, 10, 0, 0, 123_456_000);
    private static final String LEGACY_CONTENT = "마이그레이션 이전 메시지";

    @TestConfiguration(proxyBeanMethods = false)
    static class LegacyMessageMigrationConfig{
        @Bean
        FlywayMigrationStrategy legacyMessageMigrationStrategy(){
            return flyway -> {
                Flyway previousSchema = Flyway.configure()
                                .configuration(flyway.getConfiguration())
                                .target("202611121350")
                                .load();

                previousSchema.migrate();
                // updated_at 컬럼 추가 전 스키마에 기존 메시지를 저장하여 마이그레이션 후 보존 여부를 검증한다.
                JdbcTemplate jdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());
                insertLegacyData(jdbc);

                flyway.migrate();
            };
        }

        private void insertLegacyData(JdbcTemplate jdbc) {
            jdbc.update("""
                  INSERT INTO users ( id, email, nickname, role, enabled, created_at )
                  VALUES (?, ?, '기존 사용자 A', 'USER', true, ?), (?, ?, '기존 사용자 B', 'USER', true, ?)
                  """, LEGACY_USER_A_ID, "legacy-a@test.com", LEGACY_CREATED_AT,
                    LEGACY_USER_B_ID, "legacy-b@test.com", LEGACY_CREATED_AT);

            jdbc.update("""
                  INSERT INTO conversations ( id, user_a_id, user_b_id, created_at  )
                  VALUES (?, ?, ?, ?)
                  """, LEGACY_CONVERSATION_ID, LEGACY_USER_A_ID, LEGACY_USER_B_ID, LEGACY_CREATED_AT);

            jdbc.update("""
                  INSERT INTO direct_messages ( id, conversation_id, sender_id, receiver_id, content, is_read, created_at  )
                  VALUES (?, ?, ?, ?, ?, true, ?)
                  """, LEGACY_MESSAGE_ID, LEGACY_CONVERSATION_ID, LEGACY_USER_A_ID, LEGACY_USER_B_ID,
                    LEGACY_CONTENT, LEGACY_CREATED_AT);
        }
    }

    @Test
    @DisplayName("updated_at 추가 마이그레이션 후 기존 메시지를 보존하고 수정 시각은 null로 조회한다.")
    void  migration_preservesExistingMessageWithNullUpdatedAt(){
        assertThat(directMessageRepository.findById(LEGACY_MESSAGE_ID))
                .hasValueSatisfying(message ->{
                    assertThat(message.getId()).isEqualTo(LEGACY_MESSAGE_ID);
                    assertThat(message.getConversation().getId()).isEqualTo(LEGACY_CONVERSATION_ID);
                    assertThat(message.getSender().getId()).isEqualTo(LEGACY_USER_A_ID);
                    assertThat(message.getReceiver().getId()).isEqualTo(LEGACY_USER_B_ID);
                    assertThat(message.getContent()).isEqualTo(LEGACY_CONTENT);
                    assertThat(message.getCreatedAt()).isEqualTo(LEGACY_CREATED_AT);
                    assertThat(message.isRead()).isTrue();
                    assertThat(message.getUpdatedAt()).isNull();
                });
    }

    @Test
    @DisplayName("신규 메시지를 저장하면 UUID와 생성·수정 시각을 기록하고 입력 정보와 미읽음 상태를 유지한다.")
    void save_persistMessageWithAuditingAndUnreadState(){
        Conversation conversation = entityManager.find(Conversation.class, LEGACY_CONVERSATION_ID);
        String content = "새로 저장한 메시지";
        DirectMessage message = new DirectMessage(new DirectMessageCommand(
                conversation, conversation.getUserA(), conversation.getUserB(), content));

        DirectMessage saved = directMessageRepository.saveAndFlush(message);
        UUID messageId = saved.getId();
        assertThat(messageId).isNotNull();
        entityManager.clear();

        assertThat(directMessageRepository.findById(messageId))
                .hasValueSatisfying(found -> {
                    assertThat(found.getConversation().getId()).isEqualTo(LEGACY_CONVERSATION_ID);
                    assertThat(found.getSender().getId()).isEqualTo(LEGACY_USER_A_ID);
                    assertThat(found.getReceiver().getId()).isEqualTo(LEGACY_USER_B_ID);
                    assertThat(found.getContent()).isEqualTo(content);
                    assertThat(found.isRead()).isFalse();
                    assertThat(found.getCreatedAt()).isNotNull();
                    assertThat(found.getUpdatedAt()).isNotNull()
                            .isEqualTo(found.getCreatedAt());
                });

    }

    @ParameterizedTest(name = "존재하지 않는 참조대상: {0}")
    @ValueSource(strings = {"conversation", "sender", "receiver"})
    @DisplayName("대화방 또는 발신자·수신자가 존재하지 않으면 FK 제약조건 위반이 발생한다.")
    void insert_rejectsNonexistentReferences(String missingReference){
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        UUID conversationId = "conversation".equals(missingReference) ? missingId : LEGACY_CONVERSATION_ID;
        UUID senderId = "sender".equals(missingReference) ? missingId : LEGACY_USER_A_ID;
        UUID receiverId = "receiver".equals(missingReference) ? missingId : LEGACY_USER_B_ID;

        assertThatThrownBy(() ->
                jdbcTemplate.update("""
                    INSERT INTO direct_messages (id, conversation_id, sender_id, receiver_id, content, is_read, created_at)
                    VALUES (?, ?, ?, ?, ?, false, ?)
                """, UUID.randomUUID(), conversationId, senderId, receiverId, "FK 검증 메시지", LEGACY_CREATED_AT))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        e -> assertThat(e.getMostSpecificCause())
                                .isInstanceOfSatisfying(SQLException.class,
                                        cause -> assertThat(cause.getSQLState())
                                                .isEqualTo("23503")));
    }

    @ParameterizedTest(name = "null인 필수 컬럼:{0}")
    @ValueSource(strings = {
            "id",
            "conversation_id",
            "sender_id",
            "receiver_id",
            "content",
            "is_read",
            "created_at"
    })
    @DisplayName("메시지 필수 컬럼이 null이면 NOT NULL 제약조건 위반이 발생한다.")
    void insert_rejectsNullRequiredColumns(String nullColumn){
        UUID messageId = "id".equals(nullColumn) ? null : UUID.randomUUID();
        UUID conversationId = "conversation_id".equals(nullColumn) ? null : LEGACY_CONVERSATION_ID;
        UUID senderId = "sender_id".equals(nullColumn) ? null : LEGACY_USER_A_ID;
        UUID receiverId = "receiver_id".equals(nullColumn) ? null : LEGACY_USER_B_ID;
        String content = "content".equals(nullColumn) ? null : "필수 컬럼 검증 메시지";
        Boolean read = "is_read".equals(nullColumn) ? null : Boolean.FALSE;
        LocalDateTime createdAt = "created_at".equals(nullColumn) ? null : LEGACY_CREATED_AT;

        assertThatThrownBy(() ->
                jdbcTemplate.update("""
                 INSERT INTO direct_messages (id, conversation_id, sender_id, receiver_id, content, is_read, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, messageId, conversationId, senderId, receiverId, content, read, createdAt))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        e -> assertThat(e.getMostSpecificCause())
                                .isInstanceOfSatisfying(SQLException.class,
                                        cause -> assertThat(cause.getSQLState())
                                                .isEqualTo("23502")));
    }
}
