package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessageSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        showSql = false,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.generate_statistics=true"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@Testcontainers
@DisplayName("DM 메시지 조회 리포지토리")
public class DirectMessageQueryRepositoryTest {

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

    @ParameterizedTest(name = "요청자가 참여자 A={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("참여자 A와 B 모두 해당 대화방의 송수신 메시지만 목록과 전체 개수로 조회한다.")
    void searchAndSearchCount_includeOnlyTargetConversationMessages(boolean requesterIsUserA){
        User firstUser = persistUser("first@example.com", "첫 번째 사용자");
        User secondUser = persistUser("second@example.com", "두 번째 사용자");
        User thirdUser = persistUser("third@example.com", "세 번째 사용자");

        Conversation conversation = persistConversation(firstUser, secondUser);
        Conversation otherConversation = persistConversation(firstUser, thirdUser);

        DirectMessage firstToSecond = persistMessage(conversation, firstUser,
                secondUser, "첫 번째 사용자가 보낸 메시지");
        DirectMessage secondToFirst = persistMessage(conversation, secondUser,
                firstUser, "두 번째 사용자가 보낸 메시지");
        persistMessage(otherConversation, firstUser, thirdUser, "다른 대화방 메시지");

        entityManager.flush();

        UUID conversationId = conversation.getId();
        UUID requesterId = requesterIsUserA ? conversation.getUserAId()
                : conversation.getUserBId();
        UUID firstMessageId = firstToSecond.getId();
        UUID secondMessageId = secondToFirst.getId();

        entityManager.clear();

        DirectMessageSearchCondition condition = new DirectMessageSearchCondition(
                conversationId,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                DirectMessageSortBy.CREATED_AT,
                requesterId
        );

        List<DirectMessage> result = directMessageRepository.search(condition);
        long totalCount = directMessageRepository.searchCount(condition);
        assertThat(result).extracting(DirectMessage::getId)
                        .containsExactlyInAnyOrder(firstMessageId, secondMessageId);
        assertThat(totalCount).isEqualTo(2L);
    }

    @ParameterizedTest(name = "정렬 방향={0}")
    @EnumSource(SortDirection.class)
    @DisplayName("생성 시각과 ID로 양방향 커서 조회하고 커서와 관계없이 전체 개수를 유지한다.")
    void searchAndSearchCount_paginateByCreatedAtAndId(SortDirection direction){
        User requester = persistUser("requester@example.com", "요청자");
        User counterpart = persistUser("counterpart@example.com", "상대방");
        Conversation conversation = persistConversation(requester, counterpart);

        entityManager.flush();

        UUID conversationId = conversation.getId();
        UUID requesterId = requester.getId();

        List<UUID> messageIds = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        LocalDateTime base = LocalDateTime.of(2026, 11, 13, 10,
                        0, 0, 123_456_000);

        List<LocalDateTime> createdAts = List.of(base.minusNanos(1_000),
                base,
                base,
                base,
                base.plusNanos(1_000)
        );

        List.of(3,0,4,2,1).forEach(index -> insertMessage(
                        messageIds.get(index),
                        conversationId,
                        requesterId,
                        counterpart.getId(),
                        "메시지 " + index,
                        createdAts.get(index)
                ));

        entityManager.clear();
        ArrayList<UUID> expectedOrder = new ArrayList<>(messageIds);
        if (direction == SortDirection.DESCENDING){
            Collections.reverse(expectedOrder);
        }

        int limit = 2;
        String cursor = null;
        UUID idAfter = null;

        for (int offset =0; offset < expectedOrder.size(); offset += limit){
            DirectMessageSearchCondition condition = new DirectMessageSearchCondition(
                    conversationId, cursor, idAfter, limit, direction,
                    DirectMessageSortBy.CREATED_AT, requesterId);

            List<DirectMessage> page = directMessageRepository.search(condition);
            int expectedEnd = Math.min(offset + limit + 1, expectedOrder.size());

            assertThat(page).extracting(DirectMessage::getId)
                    .containsExactlyElementsOf(expectedOrder.subList(offset, expectedEnd));

            assertThat(directMessageRepository.searchCount(condition)).isEqualTo(5L);

            int lastReturnedIndex = Math.min(limit, page.size())-1;
            DirectMessage boundary = page.get(lastReturnedIndex);
            cursor = boundary.getCreatedAt().toString();
            idAfter = boundary.getId();

        }
    }

    @ParameterizedTest(name = "정렬 방향={0}")
    @EnumSource(SortDirection.class)
    @DisplayName("커서 기준 메시지가 없어도 전달받은 생성 시각과 ID로 다음 메시지를 조회한다.")
    void search_usesCursorValuesWhenBoundaryMessageDoesNotExist(SortDirection direction){
        User requester = persistUser("requester@example.com", "요청자");
        User counterpart = persistUser("counterpart@example.com", "상대방");
        Conversation conversation = persistConversation(requester, counterpart);

        entityManager.flush();

        UUID conversationId = conversation.getId();
        UUID requesterId = requester.getId();

        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID missingCursorId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 13, 10, 0, 0, 123456000);

        insertMessage(lowerId,
                conversationId,
                requesterId,
                counterpart.getId(),
                "기준 ID보다 작은 메시지",
                createdAt);
        insertMessage(higherId,
                conversationId,
                requesterId,
                counterpart.getId(),
                "기준 ID보다 큰 메시지",
                createdAt);

        entityManager.clear();

        DirectMessageSearchCondition condition = new DirectMessageSearchCondition(conversationId,
                createdAt.toString(),
                missingCursorId,
                10,
                direction,
                DirectMessageSortBy.CREATED_AT,
                requesterId);

        List<DirectMessage> result = directMessageRepository.search(condition);
        UUID expectedId = direction == SortDirection.ASCENDING ? higherId : lowerId;
        assertThat(result)
                .extracting(DirectMessage::getId)
                .containsExactly(expectedId);
    }

    @Test
    @DisplayName("조회 메시지 수가 늘어도 SQL 수가 증가하지 않고 사용자·프로필 접근 시 추가 SQL이 발생하지 않는다.")
    void search_keepsStatementCountConstantAsResultSizeGrows(){
        User requester = persistUserWithProfile(
                "requester@example.com",
                "요청자",
                "profiles/requester.png"
        );
        User counterpart = persistUserWithProfile(
                "counterpart@example.com",
                "상대방",
                "profiles/counterpart.png"
        );

        Conversation conversation = persistConversation(requester, counterpart);

        IntStream.range(0, 10).forEach(index -> {
            User sender = index % 2 == 0 ? requester : counterpart;
            User receiver = index % 2 == 0 ? counterpart : requester;

            persistMessage(conversation, sender, receiver, "프로필 조회 확인 메시지 " + index);
        });
        entityManager.flush();

        UUID conversationId = conversation.getId();
        UUID requesterId = requester.getId();

        long smallStatementCount = measureSearchStatementCount(conversationId, requesterId, 1, 2);
        long largeStatementCount = measureSearchStatementCount(conversationId, requesterId, 9, 10);

        assertThat(largeStatementCount)
                .as("조회 결과가 2개에서 10개로 늘어도 SQL 수는 동일해야 한다.")
                .isEqualTo(smallStatementCount);
    }

    private User persistUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .name(name)
                .role(Role.USER)
                .build();

        entityManager.persist(user);
        return user;
    }

    private Conversation persistConversation(User firstUser, User secondUser) {
        Conversation conversation = new Conversation(new ConversationCommand(firstUser, secondUser));

        entityManager.persist(conversation);
        return conversation;
    }

    private DirectMessage persistMessage(
            Conversation conversation,
            User sender,
            User receiver,
            String content
    ) {
        DirectMessage message = new DirectMessage(new DirectMessageCommand(
                conversation, sender, receiver, content));

        entityManager.persist(message);
        return message;
    }

    private void insertMessage(
            UUID messageId,
            UUID conversationId,
            UUID senderId,
            UUID receiverId,
            String content,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update("""
              INSERT INTO direct_messages ( id, conversation_id, sender_id, receiver_id,
                                           content, is_read, created_at, updated_at )
              VALUES (?, ?, ?, ?, ?, false, ?, ?)
              """, messageId, conversationId, senderId, receiverId, content, createdAt, createdAt);
    }

    private User persistUserWithProfile(
            String email,
            String name,
            String fileKey
    ) {
        User user = persistUser(email, name);

        FileInfo profile = FileInfo.createS3File(
                fileKey,
                "profile.png",
                "image/png",
                100L
        );

        entityManager.persist(profile);
        user.updateProfileImage(profile);

        return user;
    }

    private long measureSearchStatementCount(
            UUID conversationId,
            UUID requesterId,
            int limit,
            int expectedSize
    ) {
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();

        assertThat(statistics.isStatisticsEnabled())
                .isTrue();
        entityManager.clear();
        statistics.clear();

        DirectMessageSearchCondition condition = new DirectMessageSearchCondition(
                conversationId,
                null,
                null,
                limit,
                SortDirection.DESCENDING,
                DirectMessageSortBy.CREATED_AT,
                requesterId
        );

        List<DirectMessage> result = directMessageRepository.search(condition);
        long statementCount = statistics.getPrepareStatementCount();

        assertThat(result).hasSize(expectedSize);
        assertThat(statementCount).isPositive();

        result.forEach(message -> {
            List.of(message.getSender(), message.getReceiver())
                    .forEach(user ->{
                        assertThat(user.getName()).isNotBlank();
                        assertThat(user.getProfileImage()).isNotNull();
                        assertThat(user.getProfileImage().getKey()).isNotBlank();
                    });
        });
        assertThat(statistics.getPrepareStatementCount())
                .as("발신자·수신자와 프로필 접근 후에도 추가 SQL이 발생하지 않아야 한다.")
                .isEqualTo(statementCount);

        return statementCount;
    }
}
