package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationReader;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("대화방 서비스 통합 테스트")
public class ConversationServiceIntegrationTest {

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
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoSpyBean
    private ConversationReader conversationReader;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();
    private final UUID profileImageId = UUID.randomUUID();
    private final String profileImageKey = "profiles/" + profileImageId + ".png";

    @BeforeEach
    void setUp() {
        insertUser(requesterId, "요청자");
        insertUser(withUserId, "대화 상대");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
          DELETE FROM direct_messages
          WHERE conversation_id IN (
              SELECT id
              FROM conversations
              WHERE user_a_id IN (?, ?)
                 OR user_b_id IN (?, ?)
          )
          """,
                requesterId, withUserId,
                requesterId, withUserId
        );

        jdbcTemplate.update("""
              DELETE FROM conversations
              WHERE user_a_id IN (?, ?)
                 OR user_b_id IN (?, ?)
              """, requesterId, withUserId,
                requesterId, withUserId
        );

        jdbcTemplate.update("""
              DELETE FROM users
              WHERE id IN (?, ?)
              """, requesterId, withUserId
        );

        jdbcTemplate.update("""
              DELETE FROM file_infos
              WHERE id = ?
              """, profileImageId
        );

    }

    @Test
    @DisplayName("신규 대화방 저장을 커밋한 후 트랜잭션 밖에서 상대방의 프로필 이미지 URL을 생성한다.")
    void create_commitsNewConversationAndReturnsWithUserProfileImage(){
        insertWithUserProfileImage();
        String profileImageUrl = "https://example.com/profile.png";

        when(fileStorage.getFileUrl(profileImageKey))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    Long savedCount = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*) FROM conversations
                            WHERE (user_a_id = ? AND user_b_id = ?)
                               OR (user_a_id = ? AND user_b_id = ?)
                            """, Long.class, requesterId, withUserId, withUserId, requesterId);
                    assertThat(savedCount).isEqualTo(1L);
                    return profileImageUrl;
                });

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        ConversationDto result = conversationService.create(command);

        assertThat(result.id()).isNotNull();
        assertThat(result.with()).isEqualTo(new UserSummary(withUserId, "대화 상대", profileImageUrl));
        assertThat(result.lastestMessage()).isNull();
        assertThat(result.hasUnread()).isFalse();

        Conversation saved = conversationRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(conversationRepository.count()).isEqualTo(1L);
        assertThat(List.of(saved.getUserAId(), saved.getUserBId()))
                .containsExactlyInAnyOrder(requesterId, withUserId);

        assertThat(saved.getUserAId().toString())
                .isLessThan(saved.getUserBId().toString());
    }

    @ParameterizedTest(name = "역방향 요청 여부:{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("중복 생성 요청은 요청 방향과 관계 없이 기존 대화방과 생성 시각을 유지한다.")
    void create_returnsExistingConversationForDuplicateRequest(boolean reverseDirection){
        ConversationDto first = conversationService.create(new ConversationCreateCommand(requesterId, withUserId));
        Conversation before = conversationRepository.findById(first.id()).orElseThrow();

        assertThat(before.getCreatedAt()).isNotNull();

        ConversationCreateCommand duplicateCommand = reverseDirection ? new ConversationCreateCommand(withUserId, requesterId)
                : new ConversationCreateCommand(requesterId, withUserId);

        UserSummary expectedWith = reverseDirection ? new UserSummary(requesterId, "요청자", null)
                : new UserSummary(withUserId, "대화 상대", null);

        ConversationDto repeated = conversationService.create(duplicateCommand);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repeated.with()).isEqualTo(expectedWith);
        assertThat(repeated.lastestMessage()).isNull();
        assertThat(repeated.hasUnread()).isFalse();

        Conversation after = conversationRepository.findById(repeated.id()).orElseThrow();

        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(conversationRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기존 대화방은 최신 메시지와 요청자별 미읽음 상태를 반환한다.")
    void create_returnsExistingConversationWithLatestMessageAndRequesterUnreadState(){
        ConversationDto first = conversationService.create(
                new ConversationCreateCommand(requesterId, withUserId)
        );

        LocalDateTime olderCreatedAt = LocalDateTime.of(2026, 11, 11, 10, 0);
        LocalDateTime latestCreatedAt = olderCreatedAt.plusMinutes(1);
        UUID latestMessageId = UUID.randomUUID();

        insertMessage(
                UUID.randomUUID(),
                first.id(),
                withUserId,
                requesterId,
                "이전 미읽음 메시지",
                false,
                olderCreatedAt
        );

        insertMessage(
                latestMessageId,
                first.id(),
                requesterId,
                withUserId,
                "최신 읽은 메시지",
                true,
                latestCreatedAt
        );

        UserSummary requesterSummary = new UserSummary(requesterId, "요청자", null);
        UserSummary withUserSummary = new UserSummary(withUserId, "대화 상대", null);

        MessageDto expectedMessage = new MessageDto(latestMessageId,
                first.id(),
                latestCreatedAt,
                requesterSummary,
                withUserSummary,
                "최신 읽은 메시지");

        ConversationDto requesterResult = conversationService.create(new ConversationCreateCommand(requesterId, withUserId));
        ConversationDto withUserResult = conversationService.create(new ConversationCreateCommand(withUserId, requesterId));

        assertThat(requesterResult).isEqualTo(new ConversationDto(first.id(), withUserSummary, expectedMessage, true));
        assertThat(withUserResult).isEqualTo(new ConversationDto(first.id(), requesterSummary, expectedMessage, false));
        assertThat(conversationRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되어도 새 트랜잭션으로 저장한 대화방은 유지된다.")
    void create_keepsCommitedConversationWhenOuterTransactionRollBack(){
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        ConversationDto result = outerTransaction.execute(status -> {
            ConversationDto create = conversationService.create(command);

            jdbcTemplate.update("""
                        UPDATE users
                        SET nickname = ?
                        WHERE id = ?
                    """, "외부 트랜잭션에서 변경한 이름", requesterId);
            status.setRollbackOnly();
            return create;
        });

        assertThat(result).isNotNull();
        Conversation saved = conversationRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(conversationRepository.count()).isEqualTo(1L);
        String requesterName = jdbcTemplate.queryForObject("""
                    SELECT nickname
                    FROM users
                    WHERE id = ?
                """, String.class, requesterId);

        assertThat(requesterName).isEqualTo("요청자");
    }

    @Test
    @DisplayName("프로필 이미지 URL 생성에 실패해도 커밋된 대화방은 유지된다.")
    void create_keepsConversationWhenProfileImageUrlGenerationFails(){
        insertWithUserProfileImage();
        IllegalStateException exception = new IllegalStateException("프로필 이미지 URL 생성 실패");

        when(fileStorage.getFileUrl(profileImageKey))
                .thenThrow(exception);
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        assertThatThrownBy(() -> conversationService.create(command))
                .isSameAs(exception);

        assertThat(conversationRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("실제 UNIQUE 충돌로 저장에 실패하면 먼저 커밋된 대화방을 재조회하여 반환한다.")
    void create_returnsExistingConversationAfterActualUniqueViolation(){
        UUID existingConversationId = UUID.randomUUID();
        LocalDateTime existingCreatedAt = LocalDateTime.of(2026, 11, 11, 9, 0);

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        TransactionTemplate otherTransaction = new TransactionTemplate(transactionManager);

        otherTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicBoolean firstLookup = new AtomicBoolean(true);
        doAnswer(invocation ->{
            @SuppressWarnings("unchecked")
            Optional<ConversationDto> found = (Optional<ConversationDto>) invocation.callRealMethod();

            if (firstLookup.compareAndSet(true, false)) {
                assertThat(found).isEmpty();

                otherTransaction.executeWithoutResult(status ->
                        insertConversation(existingConversationId, existingCreatedAt));
            }
            return found;
        }).when(conversationReader).findExistingConversation(command);

        ConversationDto result = conversationService.create(command);
        assertThat(result).isEqualTo(
                new ConversationDto(
                        existingConversationId,
                        new UserSummary(withUserId, "대화 상대", null),
                        null,
                        false));

        Conversation saved = conversationRepository.findById(existingConversationId)
                .orElseThrow();
        assertThat(saved.getCreatedAt()).isEqualTo(existingCreatedAt);
        assertThat(conversationRepository.count()).isEqualTo(1L);
        verify(conversationReader, times(2)).findExistingConversation(command);
    }

    @ParameterizedTest(name = "역방향 요청 여부:{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("동일 사용자 쌍의 동시 생성 요청은 대화방 하나를 생성하고 같은 ID를 반환한다.")
    void create_returnsSameConversationForConcurrentRequests(boolean reverseDirection) throws ExecutionException, InterruptedException, TimeoutException {
        ConversationCreateCommand firstCommand = new ConversationCreateCommand(requesterId, withUserId);
        ConversationCreateCommand secondCommand = reverseDirection ? new ConversationCreateCommand(withUserId, requesterId)
                : new ConversationCreateCommand(requesterId, withUserId);
        UserSummary expectedSecondWith = reverseDirection ? new UserSummary(requesterId, "요청자", null)
                : new UserSummary(withUserId, "대화 상대", null);
        CountDownLatch initialLookups = new CountDownLatch(2);
        doAnswer(invocation ->{
            @SuppressWarnings("unchecked")
            Optional<ConversationDto> found = (Optional<ConversationDto>) invocation.callRealMethod();
            if (found.isEmpty()) {
                initialLookups.countDown();
                if (!initialLookups.await(10, TimeUnit.SECONDS)){
                    throw new IllegalStateException("두 요청의 최초 조회 대기시간 초과");
                }
            }
            return found;
        }).when(conversationReader)
                .findExistingConversation(any(ConversationCreateCommand.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ConversationDto> firstFuture = executor.submit(() -> conversationService.create(firstCommand));
            Future<ConversationDto> secondFuture = executor.submit(() -> conversationService.create(secondCommand));

            ConversationDto first = firstFuture.get(20, TimeUnit.SECONDS);
            ConversationDto second = secondFuture.get(20, TimeUnit.SECONDS);

            assertThat(initialLookups.getCount()).isZero();
            assertThat(first.id()).isNotNull();
            Conversation saved = conversationRepository.findById(first.id()).orElseThrow();
            assertThat(first).isEqualTo(new ConversationDto(
                    saved.getId(),
                    new UserSummary(withUserId, "대화 상대", null),
                    null,
                    false
            ));
            assertThat(second).isEqualTo(new ConversationDto(
                    saved.getId(),
                    expectedSecondWith,
                    null,
                    false
            ));
            assertThat(conversationRepository.count()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료 되어야 한다.")
                    .isTrue();
        }


    }
    private void insertUser(UUID userId, String name) {
        jdbcTemplate.update("""
              INSERT INTO users ( id, email, nickname, role, enabled, created_at )
              VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
              """, userId, userId + "@test.example", name
        );
    }

    private void insertWithUserProfileImage() {
        jdbcTemplate.update("""
              INSERT INTO file_infos (  id, file_key, original_filename, content_type, size, created_at )
              VALUES (?, ?, 'profile.png', 'image/png', 1024, CURRENT_TIMESTAMP)
              """, profileImageId, profileImageKey
        );

        jdbcTemplate.update("""
              UPDATE users
              SET profile_image_id = ?
              WHERE id = ?
              """,
                profileImageId,
                withUserId
        );
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
                  id, conversation_id, sender_id,
                  receiver_id,
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

    private void insertConversation(
            UUID conversationId,
            LocalDateTime createdAt
    ) {
        boolean requesterFirst = requesterId.toString().compareTo(withUserId.toString()) < 0;

        UUID userAId = requesterFirst ? requesterId : withUserId;
        UUID userBId = requesterFirst ? withUserId : requesterId;

        jdbcTemplate.update("""
              INSERT INTO conversations ( id, user_a_id, user_b_id, created_at )
              VALUES (?, ?, ?, ?)
              """, conversationId, userAId, userBId, createdAt);
    }

}
