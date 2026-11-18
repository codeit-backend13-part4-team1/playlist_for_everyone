package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageSaver;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 실제 저장 트랜잭션과 커밋 후 SSE 리스너를 연결하고, 외부 파일 조회·전송만 Mock으로 분리한다.
 * Repository Spy는 실제 조회를 수행하며 조회 여부와 읽기 트랜잭션의 완료 시점을 관찰한다.
 * 커밋 후 콜백의 단언 실패가 Spring 내부에 흡수되지 않도록 관찰값은 저장 호출이 끝난 뒤 검증한다.
 *
 * <p>클래스 전체를 테스트 트랜잭션으로 감싸지 않고, 롤백·상위 트랜잭션 시나리오마다 TransactionTemplate을 사용한다.
 * 따라서 실제 커밋 여부와 AFTER_COMMIT 실행 시점을 구분할 수 있다. URL 생성 시에는 Reader 트랜잭션의
 * afterCompletion 상태를 확인하며, 이 시점에 스레드의 모든 트랜잭션 자원이 해제됐다고 가정하지 않는다.
 * Redis 전송·HTTP 수신은 별도의 DirectMessageSseIntegrationTest에서 검증한다.
 */
@RecordApplicationEvents
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM 저장 후 SSE 이벤트 연동 통합 테스트")
class DirectMessageSseEventIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DirectMessageSaver directMessageSaver;

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private DirectMessageRepository directMessageRepository;

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoBean
    private SseEventPublisher sseEventPublisher;

    @MockitoBean
    private DirectMessagePublisher directMessagePublisher;

    private final UUID senderId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID profileImageId = UUID.randomUUID();
    private final String profileImageKey = "profiles/" + profileImageId + ".png";

    @BeforeEach
    void setUp() {
        insertUser(senderId, "발신자");
        insertUser(receiverId, "수신자");

        boolean senderComesFirst = senderId.toString().compareTo(receiverId.toString()) < 0;
        UUID userAId = senderComesFirst ? senderId : receiverId;
        UUID userBId = senderComesFirst ? receiverId : senderId;

        jdbcTemplate.update("""
                INSERT INTO conversations (id, user_a_id, user_b_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, conversationId, userAId, userBId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM direct_messages WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", senderId, receiverId);
        jdbcTemplate.update("DELETE FROM file_infos WHERE id = ?", profileImageId);
    }

    @Test
    @DisplayName("실제 DM 저장 커밋 후 저장된 정보로 수신자 SSE를 한 번 발행한다.")
    void save_publishesSseAfterCommit(ApplicationEvents applicationEvents) {
        AtomicReference<StoredMessage> visibleAtPublish = new AtomicReference<>();
        TransactionTemplate separateRead = new TransactionTemplate(transactionManager);
        separateRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        separateRead.setReadOnly(true);

        doAnswer(invocation -> {
            SseEvent<?> event = invocation.getArgument(0);
            visibleAtPublish.set(separateRead.execute(status -> findStoredMessage(event.eventId())));
            return null;
        }).when(sseEventPublisher).publish(any());

        DirectMessage saved = directMessageSaver.save(command("커밋된 메시지"));

        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(saved.getId()));
        StoredMessage stored = visibleAtPublish.get();
        assertThat(stored).isNotNull();
        assertThat(stored.id()).isEqualTo(saved.getId());
        assertThat(stored.createdAt()).isNotNull();
        assertThat(stored.read()).isFalse();
        assertThat(countSavedMessages()).isEqualTo(1L);

        SseEvent<?> published = onlyPublishedEvent();
        assertThat(published.eventId()).isEqualTo(stored.id());
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.eventName()).isEqualTo("direct-messages");
        assertThat(published.data()).isEqualTo(new MessageDto(
                stored.id(), stored.conversationId(), stored.createdAt(),
                new UserSummary(stored.senderId(), "발신자", null),
                new UserSummary(stored.receiverId(), "수신자", null), stored.content()
        ));
        verify(directMessageRepository).findDetailedById(saved.getId());
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("커밋 전에는 DM 이벤트를 처리하지 않고 명시적으로 롤백하면 조회와 SSE 발행을 실행하지 않는다.")
    void doesNotProcessBeforeCommitOrAfterRollback(ApplicationEvents applicationEvents) {
        UUID directMessageId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            insertMessage(directMessageId, "롤백할 메시지");
            eventPublisher.publishEvent(event(directMessageId));

            // TransactionTemplate의 본문에서 실패한 검증은 호출한 테스트로 직접 전달된다.
            verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
            verifyNoInteractions(fileStorage, sseEventPublisher);
            status.setRollbackOnly();
        });

        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(directMessageId));
        assertThat(countSavedMessages()).isZero();
        verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
        verifyNoInteractions(fileStorage, sseEventPublisher);
    }

    @Test
    @DisplayName("실제 DB 저장이 실패하면 생성 이벤트가 발행됐어도 메시지와 SSE 후속 처리를 남기지 않는다.")
    void save_doesNotPublishWhenDatabaseCommitFails(ApplicationEvents applicationEvents) {
        String invalidContent = "DB 저장 실패 검증" + (char) 0;

        assertThatThrownBy(() -> directMessageSaver.save(command(invalidContent)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList()).hasSize(1);
        assertThat(countSavedMessages()).isZero();
        verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
        verifyNoInteractions(fileStorage, sseEventPublisher);
    }

    @Test
    @DisplayName("트랜잭션 없이 DM 생성 이벤트를 발행하면 메시지가 존재해도 조회와 SSE 발행을 실행하지 않는다.")
    void doesNotProcessEventWithoutTransaction(ApplicationEvents applicationEvents) {
        UUID directMessageId = UUID.randomUUID();
        insertMessage(directMessageId, "이미 저장된 메시지");
        StoredMessage before = findStoredMessage(directMessageId);

        eventPublisher.publishEvent(event(directMessageId));

        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(directMessageId));
        assertThat(findStoredMessage(directMessageId)).isEqualTo(before);
        verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
        verifyNoInteractions(fileStorage, sseEventPublisher);
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되어도 독립적으로 커밋한 DM과 수신자 SSE 발행은 유지한다.")
    void save_keepsMessageAndSseWhenOuterTransactionRollsBack(ApplicationEvents applicationEvents) {
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        DirectMessage saved = outerTransaction.execute(status -> {
            DirectMessage message = directMessageSaver.save(command("외부 롤백 후에도 유지할 메시지"));
            jdbcTemplate.update("UPDATE users SET nickname = ? WHERE id = ?", "롤백할 이름", senderId);
            status.setRollbackOnly();
            return message;
        });

        assertThat(saved).isNotNull();
        assertThat(countSavedMessages()).isEqualTo(1L);
        assertThat(findStoredMessage(saved.getId()).content()).isEqualTo("외부 롤백 후에도 유지할 메시지");
        assertThat(jdbcTemplate.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, senderId))
                .isEqualTo("발신자");
        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(saved.getId()));

        SseEvent<?> published = onlyPublishedEvent();
        assertThat(published.eventId()).isEqualTo(saved.getId());
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class,
                dto -> assertThat(dto.id()).isEqualTo(saved.getId()));
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("새 읽기 전용 트랜잭션에서 상세 조회하고 조회 트랜잭션 종료 후 프로필 URL과 SSE DTO를 구성한다.")
    void save_convertsProfileAfterReadTransactionCompletes() {
        insertReceiverProfileImage();
        String profileImageUrl = "https://example.com/profile.png";
        AtomicBoolean readTransactionActive = new AtomicBoolean();
        AtomicBoolean readOnly = new AtomicBoolean();
        AtomicInteger completionStatus = new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);
        AtomicReference<Integer> completionAtUrlCreation = new AtomicReference<>();
        AtomicReference<Integer> completionAtPublish = new AtomicReference<>();
        Answer<?> actualRepositoryQuery = mockingDetails(directMessageRepository)
                .getMockCreationSettings().getDefaultAnswer();

        doAnswer(invocation -> {
            readTransactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            readOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        completionStatus.set(status);
                    }
                });
            }
            // Spring Data의 인터페이스 메서드는 기본 Answer를 통해 원본 Repository 프록시로 위임한다.
            return actualRepositoryQuery.answer(invocation);
        }).when(directMessageRepository).findDetailedById(any(UUID.class));

        when(fileStorage.getFileUrl(profileImageKey)).thenAnswer(invocation -> {
            // 이후 값이 바뀌어도 변환 시점의 완료 상태를 검증할 수 있도록 복사한다.
            completionAtUrlCreation.set(completionStatus.get());
            return profileImageUrl;
        });
        doAnswer(invocation -> {
            completionAtPublish.set(completionStatus.get());
            return null;
        }).when(sseEventPublisher).publish(any());

        DirectMessage saved = directMessageSaver.save(command("프로필이 포함된 메시지"));

        assertThat(readTransactionActive.get()).isTrue();
        assertThat(readOnly.get()).isTrue();
        assertThat(completionAtUrlCreation.get()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(completionAtPublish.get()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        verify(directMessageRepository).findDetailedById(saved.getId());
        verify(fileStorage).getFileUrl(profileImageKey);
        verifyNoMoreInteractions(fileStorage);

        SseEvent<?> published = onlyPublishedEvent();
        assertThat(published.eventId()).isEqualTo(saved.getId());
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class, dto -> {
            assertThat(dto.sender()).isEqualTo(new UserSummary(senderId, "발신자", null));
            assertThat(dto.receiver()).isEqualTo(new UserSummary(receiverId, "수신자", profileImageUrl));
        });
    }

    @Test
    @DisplayName("SSE용 메시지 조회에 실패해도 저장된 DM을 유지하고 WebSocket 발행을 실행한다.")
    void send_publishesWebSocketWhenSseMessageLookupFails() {
        doThrow(new DataAccessResourceFailureException("SSE용 메시지 조회 실패"))
                .when(directMessageRepository).findDetailedById(any(UUID.class));
        String content = "SSE 조회 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        verify(directMessageRepository).findDetailedById(result.id());
        verifyNoInteractions(fileStorage, sseEventPublisher);
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
    }

    @Test
    @DisplayName("SSE용 프로필 URL 생성에 실패해도 저장된 DM을 유지하고 WebSocket 발행을 실행한다.")
    void send_publishesWebSocketWhenSseProfileUrlGenerationFails() {
        insertReceiverProfileImage();
        String profileImageUrl = "https://example.com/profile.png";
        IllegalStateException cause = new IllegalStateException("SSE용 프로필 URL 생성 실패");
        // 동기 SSE 리스너에서 먼저 변환하고, Saver 반환 후 송신 응답을 다시 변환한다.
        when(fileStorage.getFileUrl(profileImageKey))
                .thenThrow(cause)
                .thenReturn(profileImageUrl);
        String content = "SSE URL 생성 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.receiver()).isEqualTo(new UserSummary(receiverId, "수신자", profileImageUrl));
        verify(directMessageRepository).findDetailedById(result.id());
        verify(fileStorage, times(2)).getFileUrl(profileImageKey);
        verifyNoMoreInteractions(fileStorage);
        verifyNoInteractions(sseEventPublisher);
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
    }

    @Test
    @DisplayName("SSE 발행에 실패해도 저장된 DM을 유지하고 재시도 없이 WebSocket 발행을 실행한다.")
    void send_publishesWebSocketWhenSsePublishingFails() {
        SsePublishException cause = new SsePublishException(new IllegalStateException("SSE 발행 실패"));
        doThrow(cause).when(sseEventPublisher).publish(any());
        String content = "SSE 발행 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        SseEvent<?> attempted = onlyPublishedEvent();
        assertThat(attempted.eventId()).isEqualTo(result.id());
        assertThat(attempted.receiverId()).isEqualTo(receiverId);
        assertThat(attempted.data()).isInstanceOfSatisfying(MessageDto.class,
                dto -> assertThat(dto.id()).isEqualTo(result.id()));
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("WebSocket용 프로필 URL 생성에 실패하면 오류를 반환하고 저장된 DM과 앞선 SSE 발행을 유지한다.")
    void send_keepsSsePublicationWhenWebSocketProfileUrlGenerationFails() {
        insertReceiverProfileImage();
        String profileImageUrl = "https://example.com/profile.png";
        IllegalStateException cause = new IllegalStateException("WebSocket용 프로필 URL 생성 실패");
        // SSE 변환을 성공시킨 뒤 송신 응답 변환에서만 실패시킨다.
        when(fileStorage.getFileUrl(profileImageKey))
                .thenReturn(profileImageUrl)
                .thenThrow(cause);
        String content = "WebSocket URL 생성 실패 후에도 유지할 메시지";

        Throwable thrown = catchThrowable(() -> directMessageService.send(command(content)));

        SseEvent<?> published = onlyPublishedEvent();
        assertThat(thrown).isInstanceOfSatisfying(DirectMessagePublishException.class, exception -> {
            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getDetails())
                    .containsEntry("directMessageId", published.eventId())
                    .containsEntry("conversationId", conversationId)
                    .containsEntry("persisted", true);
        });
        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(published.eventId());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class, dto -> {
            assertThat(dto.id()).isEqualTo(stored.id());
            assertThat(dto.content()).isEqualTo(content);
            assertThat(dto.receiver()).isEqualTo(new UserSummary(receiverId, "수신자", profileImageUrl));
        });
        verify(fileStorage, times(2)).getFileUrl(profileImageKey);
        verifyNoMoreInteractions(fileStorage);
        verifyNoInteractions(directMessagePublisher);
    }

    @Test
    @DisplayName("WebSocket 발행에 실패하면 오류를 반환하고 저장된 DM과 앞선 SSE 발행을 재시도 없이 유지한다.")
    void send_keepsSsePublicationWhenWebSocketPublishingFails() {
        IllegalStateException cause = new IllegalStateException("WebSocket 발행 실패");
        doAnswer(invocation -> {
            MessageDto message = invocation.getArgument(0);
            throw new DirectMessagePublishException(message.id(), message.conversationId(), cause);
        }).when(directMessagePublisher).publish(any(MessageDto.class));
        String content = "WebSocket 발행 실패 후에도 유지할 메시지";

        Throwable thrown = catchThrowable(() -> directMessageService.send(command(content)));

        SseEvent<?> published = onlyPublishedEvent();
        assertThat(thrown).isInstanceOfSatisfying(DirectMessagePublishException.class, exception -> {
            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getDetails())
                    .containsEntry("directMessageId", published.eventId())
                    .containsEntry("conversationId", conversationId)
                    .containsEntry("persisted", true);
        });
        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(published.eventId());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class,
                dto -> assertThat(dto.id()).isEqualTo(stored.id()));
        ArgumentCaptor<MessageDto> messageCaptor = ArgumentCaptor.captor();
        verify(directMessagePublisher).publish(messageCaptor.capture());
        MessageDto attempted = messageCaptor.getValue();
        assertThat(attempted.id()).isEqualTo(stored.id());
        assertThat(attempted.conversationId()).isEqualTo(conversationId);
        assertThat(attempted.content()).isEqualTo(content);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
    }

    private DirectMessageSendCommand command(String content) {
        return new DirectMessageSendCommand(conversationId, senderId, content);
    }

    private DirectMessageCreatedEvent event(UUID directMessageId) {
        return new DirectMessageCreatedEvent(directMessageId, conversationId, senderId, "발신자", receiverId);
    }

    private SseEvent<?> onlyPublishedEvent() {
        ArgumentCaptor<SseEvent<?>> captor = ArgumentCaptor.captor();
        verify(sseEventPublisher).publish(captor.capture());
        verifyNoMoreInteractions(sseEventPublisher);
        return captor.getValue();
    }

    private long countSavedMessages() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM direct_messages WHERE conversation_id = ?", Long.class, conversationId);
    }

    private StoredMessage findStoredMessage(UUID directMessageId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, conversation_id, sender_id, receiver_id, content, created_at, is_read
                FROM direct_messages
                WHERE id = ? AND conversation_id = ?
                """, (row, rowNum) -> new StoredMessage(
                row.getObject("id", UUID.class), row.getObject("conversation_id", UUID.class),
                row.getObject("sender_id", UUID.class), row.getObject("receiver_id", UUID.class),
                row.getString("content"), row.getTimestamp("created_at").toLocalDateTime(),
                row.getBoolean("is_read")
        ), directMessageId, conversationId);
    }

    private void insertUser(UUID userId, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, userId, userId + "@test.example", nickname);
    }

    private void insertMessage(UUID directMessageId, String content) {
        jdbcTemplate.update("""
                INSERT INTO direct_messages (
                    id, conversation_id, sender_id, receiver_id, content, is_read, created_at
                )
                VALUES (?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP)
                """, directMessageId, conversationId, senderId, receiverId, content);
    }

    private void insertReceiverProfileImage() {
        jdbcTemplate.update("""
                INSERT INTO file_infos (id, file_key, original_filename, content_type, size, created_at)
                VALUES (?, ?, 'profile.png', 'image/png', 1024, CURRENT_TIMESTAMP)
                """, profileImageId, profileImageKey);
        jdbcTemplate.update("UPDATE users SET profile_image_id = ? WHERE id = ?", profileImageId, receiverId);
    }

    private record StoredMessage(
            UUID id,
            UUID conversationId,
            UUID senderId,
            UUID receiverId,
            String content,
            LocalDateTime createdAt,
            boolean read
    ) {
    }
}
