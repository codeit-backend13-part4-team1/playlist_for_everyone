package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import java.util.Optional;
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
@DisplayName("대화방 리포지토리")
class ConversationRepositoryTest {

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
    private ConversationRepository conversationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("해당 사용자 쌍의 대화방이 없으면 빈 Optional을 반환한다.")
    void findDetailByUserPair_returnsEmptyWhenConversationDoesNotExist(){
        User requester = persistUser("requester@example.com", "요청자");
        User otherUser = persistUser("other@example.com", "대화 상대");

        ConversationCommand pair = new ConversationCommand(requester, otherUser);

        entityManager.flush();

        Optional<ConversationDetailProjection> result = conversationRepository.findDetailByUserPair(pair.userA().getId(), pair.userB().getId(), requester.getId());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("메시지가 없는 대화방을 조회하면 최신 메시지는 null이고 미읽음 여부는 false이다.")
    void findDetailByUserPair_returnsConversationWithoutMessages(){
        User requester = persistUser("requester@example.com", "요청자");
        User otherUser = persistUser("other@example.com", "대화 상대");

        ConversationCommand pair = new ConversationCommand(requester, otherUser);
        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        UUID conversationId = saved.getId();
        UUID userAId = pair.userA().getId();
        UUID userBId = pair.userB().getId();
        UUID requesterId = requester.getId();

        entityManager.clear();

        Optional<ConversationDetailProjection> result = conversationRepository.findDetailByUserPair(userAId, userBId, requesterId);
        assertThat(result).hasValueSatisfying(projection ->{
            Conversation found = projection.getConversation();

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(conversationId);
            assertThat(found.getUserAId()).isEqualTo(userAId);
            assertThat(found.getUserBId()).isEqualTo(userBId);
            assertThat(projection.getLastMessage()).isNull();
            assertThat(projection.getHasUnread()).isFalse();
        });
    }

    @Test
    @DisplayName("메시지의 삽입 순서와 ID 순서에 관계없이 생성 시각이 가장 최근인 메시지를 조회한다.")
    void findDetailByUserPair_returnsLatestMessageByCreatedAt(){
        User requester = persistUser("requester@example.com", "요청자");
        User otherUser = persistUser("other@example.com", "대화 상대");
        ConversationCommand pair = new ConversationCommand(requester, otherUser);
        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        LocalDateTime latestCreatedAt = LocalDateTime.of(2026, 11, 11, 10, 0);
        UUID latestMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID olderMessageId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        insertMessage(latestMessageId, saved.getId(), requester.getId(), otherUser.getId(),"최신 메시지", true, latestCreatedAt);
        insertMessage(olderMessageId, saved.getId(), otherUser.getId(), requester.getId(),"이전 메시지", true, latestCreatedAt.minusMinutes(1));

        entityManager.clear();
        ConversationDetailProjection result = conversationRepository.findDetailByUserPair(
                pair.userA().getId(),
                pair.userB().getId(),
                requester.getId()
        ).orElseThrow();

        DirectMessage latestMessage = result.getLastMessage();
        assertThat(latestMessage).isNotNull();
        assertThat(latestMessage.getId()).isEqualTo(latestMessageId);
        assertThat(latestMessage.getConversation().getId()).isEqualTo(saved.getId());
        assertThat(latestMessage.getCreatedAt()).isEqualTo(latestCreatedAt);
        assertThat(latestMessage.getSender().getId()).isEqualTo(requester.getId());
        assertThat(latestMessage.getReceiver().getId()).isEqualTo(otherUser.getId());
        assertThat(latestMessage.getContent()).isEqualTo("최신 메시지");
    }

    @Test
    @DisplayName("메시지 생성 시각이 같으면 ID가 가장 큰 메시지를 최신 메시지로 조회한다.")
    void findDetailByUserPair_returnsHighestIdWhenCreatedAtIsEqual(){
        User requester = persistUser("requester@example.com", "요청자");
        User otherUser = persistUser("other@example.com", "대화 상대");

        ConversationCommand pair = new ConversationCommand(requester, otherUser);
        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 11, 10, 0);
        UUID lowestId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID middleId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID highestId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        insertMessage(middleId, saved.getId(), otherUser.getId(), requester.getId(), "중간 ID 메시지", true, createdAt);
        insertMessage(highestId, saved.getId(), otherUser.getId(), requester.getId(), "가장 큰 ID 메시지", true, createdAt);
        insertMessage(lowestId, saved.getId(), otherUser.getId(), requester.getId(), "가장 작은 ID 메시지", true, createdAt);

        entityManager.clear();

        ConversationDetailProjection result = conversationRepository.findDetailByUserPair(
                pair.userA().getId(),
                pair.userB().getId(),
                requester.getId()
        ).orElseThrow();

        assertThat(result.getLastMessage()).isNotNull();
        assertThat(result.getLastMessage().getId()).isEqualTo(highestId);

    }

    @ParameterizedTest(name = "requesterIsUserA={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("요청자가 어느 참여자이든 읽지 않은 수신 메시지가 있으면 미읽음 여부는 true이다.")
    void findDetailByUserPair_hasUnreadWhenRequesterHasUnreadReceivedMessage(boolean requesterIsUserA){
        User firstUser = persistUser("first@example.com", "첫 번째 사용자");
        User secondUser = persistUser("second@example.com", "두 번째 사용자");

        ConversationCommand pair = new ConversationCommand(firstUser, secondUser);
        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        User requester = requesterIsUserA ? pair.userA() : pair.userB();
        User otherUser = requesterIsUserA ? pair.userB() : pair.userA();

        insertMessage(UUID.randomUUID(),
                saved.getId(),
                otherUser.getId(),
                requester.getId(),
                "읽지 않은 수신 메시지",
                false,
                LocalDateTime.of(2026, 11, 11, 10, 0));

        entityManager.clear();
        ConversationDetailProjection result = conversationRepository.findDetailByUserPair(
                pair.userA().getId(),
                pair.userB().getId(),
                requester.getId()
        ).orElseThrow();

        assertThat(result.getHasUnread()).isTrue();
    }

    @Test
    @DisplayName("요청자가 받은 메시지를 모두 읽었으면 미읽음 여부는 false이다.")
    void findDetailByUserPair_hasNoUnreadWhenAllReceivedMessagesAreRead(){
        User requester = persistUser("requester@example.com", "요청자");
        User otherUser = persistUser("other@example.com", "대화 상대");

        ConversationCommand pair = new ConversationCommand(requester, otherUser);
        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 11, 10, 0);

        insertMessage(
                UUID.randomUUID(),
                saved.getId(),
                otherUser.getId(),
                requester.getId(),
                "읽은 첫 번째 메시지",
                true,
                createdAt
        );
        insertMessage(
                UUID.randomUUID(),
                saved.getId(),
                otherUser.getId(),
                requester.getId(),
                "읽은 두 번째 메시지",
                true,
                createdAt.plusMinutes(1)
        );

        entityManager.clear();
        ConversationDetailProjection result = conversationRepository.findDetailByUserPair(
                pair.userA().getId(),
                pair.userB().getId(),
                requester.getId()
        ).orElseThrow();

        assertThat(result.getHasUnread()).isFalse();
    }

    @Test
    @DisplayName("요청자가 보낸 메시지를 상대방이 읽지 않았어도 요청자의 미읽음 여부는 false이다.")
    void findDetailByUserPair_hasNoUnreadForUnreadSentMessage(){
        User requester = persistUser("requester@example.com", "요청자");
        User otherUser = persistUser("other@example.com", "대화 상대");

        ConversationCommand pair = new ConversationCommand(requester, otherUser);
        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        insertMessage(UUID.randomUUID(),
                saved.getId(),
                requester.getId(),
                otherUser.getId(),
                "상대방이 아직 읽지 않은 메시지",
                false,
                LocalDateTime.of(2026, 11, 11, 10, 0));

        entityManager.clear();
        ConversationDetailProjection result = conversationRepository.findDetailByUserPair(
                pair.userA().getId(),
                pair.userB().getId(),
                requester.getId()
        ).orElseThrow();

        assertThat(result.getHasUnread()).isFalse();
    }

    @Test
    @DisplayName("최신 수신 메시지를 읽었어도 이전에 읽지 않은 수신 메시지가 있으면 미읽음 여부는 true이다.")
    void findDetailByUserPair_hasUnreadWhenOlderReceivedMessageIsUnread(){
        User requester = persistUser("requester@example.com", "요청자");
        User otherUser = persistUser("other@example.com", "대화 상대");

        ConversationCommand pair = new ConversationCommand(requester, otherUser);
        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 11, 10, 0);

        insertMessage(UUID.randomUUID(), saved.getId(), otherUser.getId(), requester.getId(), "이전에 받은 미읽음 메시지", false, createdAt);
        insertMessage(UUID.randomUUID(), saved.getId(), otherUser.getId(), requester.getId(), "최근에 받은 읽은 메시지", true, createdAt.plusMinutes(1));

        entityManager.clear();
        ConversationDetailProjection result = conversationRepository.findDetailByUserPair(
                pair.userA().getId(),
                pair.userB().getId(),
                requester.getId()
        ).orElseThrow();

        assertThat(result.getHasUnread()).isTrue();
    }

    @Test
    @DisplayName("다른 대화방의 최신 메시지와 미읽음 메시지는 조회 대상 대화방에 반영하지 않는다.")
    void findDetailByUserPair_excludesMessagesFromOtherConversations(){
        User requester = persistUser("requester@example.com", "요청자");
        User partner = persistUser("partner@example.com", "조회 대상 대화 상대");
        User anotherPartner = persistUser("another@example.com", "다른 대화방 상대");

        Conversation otherConversation = conversationRepository.saveAndFlush(
                new Conversation(new ConversationCommand(requester, anotherPartner)));
        ConversationCommand targetPair = new ConversationCommand(requester, partner);
        Conversation targetConversation = conversationRepository.saveAndFlush(new Conversation(targetPair));
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 11, 10, 0);
        UUID targetMessageId = UUID.randomUUID();

        insertMessage(targetMessageId,
                targetConversation.getId(),
                partner.getId(),
                requester.getId(),
                "조회 대상 대화방의 읽은 메시지",
                true,
                createdAt);
        insertMessage(UUID.randomUUID(),
                otherConversation.getId(),
                anotherPartner.getId(),
                requester.getId(),
                "다른 대화방의 더 최근 미읽음 메시지",
                false,
                createdAt.plusMinutes(1));

        entityManager.clear();
        ConversationDetailProjection result = conversationRepository.findDetailByUserPair(
                targetPair.userA().getId(),
                targetPair.userB().getId(),
                requester.getId()
        ).orElseThrow();

        assertThat(result.getConversation().getId())
                .isEqualTo(targetConversation.getId());
        assertThat(result.getLastMessage()).isNotNull();
        assertThat(result.getLastMessage().getId()).isEqualTo(targetMessageId);
        assertThat(result.getHasUnread()).isFalse();
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

    private void insertMessage(
            UUID messageId,
            UUID conversationId,
            UUID senderId,
            UUID receiverId,
            String content,
            boolean read,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update("""
              INSERT INTO direct_messages (
                  id, conversation_id, sender_id,receiver_id,
                  content, is_read, created_at
              )
              VALUES (?, ?, ?, ?, ?, ?, ?)
              """,
                messageId,
                conversationId,
                senderId,
                receiverId,
                content,
                read,
                createdAt
        );
    }
}
