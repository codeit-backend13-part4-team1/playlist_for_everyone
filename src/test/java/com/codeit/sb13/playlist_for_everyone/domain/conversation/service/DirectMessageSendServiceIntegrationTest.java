package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 실제 PostgreSQL과 서비스 프록시로 저장 커밋·실패·외부 트랜잭션 롤백의 경계를 검증한다.
 * FileStorage와 DirectMessagePublisher는 Mock이다. 실제 S3·Redis·WebSocket 수신은 이 테스트의 범위가 아니다.
 * 클래스 전체를 롤백 트랜잭션으로 감싸지 않고 독립 커밋한 준비 데이터와 메시지를 직접 정리한다.
 */
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.docker.compose.enabled=false"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM 메시지 송신 서비스 통합 테스트")
public class DirectMessageSendServiceIntegrationTest {

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
    private DirectMessageService directMessageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoBean
    private DirectMessagePublisher directMessagePublisher;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID counterpartId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID profileImageId = UUID.randomUUID();
    private final String profileImageKey = "profiles/" + profileImageId + ".png";


    @BeforeEach
    void setUp() {
        insertUser(requesterId, "요청자");
        insertUser(counterpartId, "대화 상대");
        insertUser(otherUserId, "비참여자");

        insertConversation(conversationId, requesterId, counterpartId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
              DELETE FROM direct_messages
              WHERE conversation_id = ?
              """, conversationId);

        jdbcTemplate.update("""
              DELETE FROM conversations
              WHERE id = ?
              """, conversationId);

        jdbcTemplate.update("""
              DELETE FROM users
              WHERE id IN (?, ?, ?)
              """, requesterId, counterpartId, otherUserId);

        jdbcTemplate.update("""
          DELETE FROM file_infos
          WHERE id = ?
          """, profileImageId);
    }

    @ParameterizedTest(name = "반대 방향:{0}, 프로필 이미지 존재: {1}")
    @CsvSource({
            "false, false",
            "false, true",
            "true, false",
            "true, true"
    })
    @DisplayName("참여자가 메시지를 전송하면 저장을 커밋한 후 응답을 변환하고 발행한다.")
    void send_commitsMessageBeforePublishing(boolean reverseDirection, boolean hasProfileImage){
        UUID senderId = reverseDirection ? counterpartId : requesterId;
        UUID receiverId = reverseDirection ? requesterId : counterpartId;

        String senderName = reverseDirection ? "대화 상대" : "요청자";
        String receiverName = reverseDirection ? "요청자" : "대화 상대";
        String content = "안녕하세요.";
        String profileImageUrl = "https://example.com/profile.png";

        if (hasProfileImage) {
            insertCounterpartProfileImage();
            when(fileStorage.getFileUrl(profileImageKey))
                    .thenAnswer(invocation ->{
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                                .isFalse();

                        Long savedCount = getSavedCount();
                        assertThat(savedCount).isEqualTo(1L);
                        return profileImageUrl;
                    });
        }

        doAnswer(invocation ->{
            MessageDto published = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

            Long savedCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM direct_messages
                        WHERE id = ?
                            AND conversation_id = ?
                            AND sender_id = ?
                            AND receiver_id = ?
                            AND content = ?
                            AND is_read = false
                    """, Long.class, published.id(), conversationId, senderId, receiverId, content);

            assertThat(savedCount).isEqualTo(1L);
            return null;
        }).when(directMessagePublisher).publish(any(MessageDto.class));

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, senderId, content);
        MessageDto result = directMessageService.send(command);

        String senderProfileImageUrl = hasProfileImage && reverseDirection ? profileImageUrl : null;
        String receiverProfileImageUrl = hasProfileImage && !reverseDirection ? profileImageUrl : null;

        assertThat(result.id()).isNotNull();
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.conversationId()).isEqualTo(conversationId);
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.sender())
                .isEqualTo(new UserSummary(senderId, senderName, senderProfileImageUrl));
        assertThat(result.receiver())
                .isEqualTo(new UserSummary(receiverId, receiverName, receiverProfileImageUrl));

        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        if (hasProfileImage){
            verify(fileStorage).getFileUrl(profileImageKey);
            verifyNoMoreInteractions(fileStorage);
            return;
        }
        verifyNoInteractions(fileStorage);
    }

    @ParameterizedTest(name = "대화방 미존재 여부{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("대화방이 없거나 비참여자가 전송하면 메시지를 저장하거나 발행하지 않는다.")
    void send_doesNotSaveOrPublishWhenConversationIsUnavailable(boolean missingConversation){
        UUID targetConversationId = missingConversation ? UUID.randomUUID() : conversationId;
        UUID senderId = missingConversation ? requesterId : otherUserId;

        DirectMessageSendCommand command = new DirectMessageSendCommand(targetConversationId, senderId, "전송할 수 없는 메시지");

        assertThatThrownBy(() -> directMessageService.send(command))
                .isInstanceOf(ConversationNotFoundException.class);

        Long savedCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM direct_messages
                    WHERE conversation_id IN (?, ?)
                """, Long.class, conversationId, targetConversationId);

        assertThat(savedCount).isZero();
        verifyNoInteractions(fileStorage, directMessagePublisher);

    }

    @Test
    @DisplayName("DB 저장이 실패하면 메시지를 남기지 않고 발행하지 않는다.")
    void send_doesNotPublishWhenDatabaseSaveFails(){
        String invalidContent = "DB 저장 실패 검증" + (char) 0;
        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, invalidContent);

        assertThatThrownBy(() -> directMessageService.send(command))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long savedCount = getSavedCount();

        assertThat(savedCount).isZero();
        verifyNoInteractions(fileStorage, directMessagePublisher);
    }

    @Test
    @DisplayName("프로필 이미지 URL 생성에 실패해도 저장된 메시지를 유지하고 발행하지 않는다.")
    void send_keepsSavedMessageWhenProfileImageUrlGenerationFails(){
        insertCounterpartProfileImage();

        IllegalStateException cause = new IllegalStateException("프로필 이미지 URL 생성 실패");

        when(fileStorage.getFileUrl(profileImageKey))
                .thenThrow(cause);

        String content = "저장이 유지되어야 하는 메시지";
        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, content);

        assertThatThrownBy(()-> directMessageService.send(command))
                .isInstanceOfSatisfying(DirectMessagePublishException.class,
                        e -> {
                    assertThat(e.getCause()).isSameAs(cause);
                            UUID savedMessageId = getSavedMessageId(content);

                            assertThat(e.getDetails())
                                    .containsEntry("directMessageId", savedMessageId)
                                    .containsEntry("conversationId", conversationId)
                                    .containsEntry("persisted", true);
                        });

        verify(fileStorage).getFileUrl(profileImageKey);
        verifyNoInteractions(directMessagePublisher);
    }

    @Test
    @DisplayName("실시간 발행에 실패해도 저장된 메시지를 유지하고 재발행하지 않는다.")
    void send_keepsSavedMessageWhenPublishingFails(){
        IllegalStateException cause = new IllegalStateException("메시지 브로커 발행 실패");

        doAnswer(invocation ->{
            MessageDto message = invocation.getArgument(0);
            throw new DirectMessagePublishException(
                    message.id(),
                    message.conversationId(),
                    cause
            );
        }).when(directMessagePublisher).publish(any(MessageDto.class));

        String content ="발행 실패 후에도 유지할 메시지";
        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, content);

        assertThatThrownBy(() -> directMessageService.send(command))
                .isInstanceOfSatisfying(DirectMessagePublishException.class,
                        e ->{
                    assertThat(e.getCause()).isSameAs(cause);
                    UUID savedMessageId = getSavedMessageId(content);

                    assertThat(e.getDetails())
                            .containsEntry("directMessageId", savedMessageId)
                            .containsEntry("conversationId", conversationId)
                            .containsEntry("persisted", true);
                });

        verify(directMessagePublisher).publish(any(MessageDto.class));
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되어도 새 트랜잭션으로 저장한 메시지는 유지된다.")
    void send_keepsCommittedMessageWhenOuterTransactionRollsBack(){
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        String content = "외부 롤백 후에도 유지할 메시지";

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, content);

        MessageDto result = outerTransaction.execute(status -> {
            MessageDto sentMessage = directMessageService.send(command);

            jdbcTemplate.update("""
                    UPDATE users
                    SET nickname = ?
                    WHERE id = ?
                    """, "외부 트랜잭션에서 변경한 이름", requesterId);

            status.setRollbackOnly();
            return sentMessage;
        });

        assertThat(result).isNotNull();
        assertThat(getSavedMessageId(content)).isEqualTo(result.id());
        String requesterName = jdbcTemplate.queryForObject("""
                    SELECT nickname
                    FROM users
                    WHERE id =?
                """, String.class, requesterId);

        assertThat(requesterName).isEqualTo("요청자");

        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
    }



    private Long getSavedCount() {
        return jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM direct_messages
                    WHERE conversation_id = ?
                """, Long.class, conversationId);
    }

    private UUID getSavedMessageId(String content) {
        return jdbcTemplate.queryForObject("""
                    SELECT id
                    FROM direct_messages
                    WHERE conversation_id = ?
                        AND sender_id = ?
                        AND receiver_id = ?
                        AND content = ?
                        AND is_read = false
                """, UUID.class, conversationId, requesterId, counterpartId, content);
    }

    private void insertUser(UUID userId, String nickname) {
        jdbcTemplate.update("""
              INSERT INTO users ( id, email, nickname, role, enabled, created_at  )
              VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
              """, userId, userId + "@test.example", nickname);
    }

    private void insertConversation(
            UUID conversationId,
            UUID firstUserId,
            UUID secondUserId
    ) {
        boolean firstUserComesFirst = firstUserId.toString().compareTo(secondUserId.toString()) < 0;

        UUID userAId = firstUserComesFirst ? firstUserId : secondUserId;
        UUID userBId = firstUserComesFirst ? secondUserId : firstUserId;

        jdbcTemplate.update("""
              INSERT INTO conversations ( id, user_a_id, user_b_id, created_at )
              VALUES (?, ?, ?, CURRENT_TIMESTAMP)
              """, conversationId, userAId, userBId);
    }

    private void insertCounterpartProfileImage() {
        jdbcTemplate.update("""
              INSERT INTO file_infos ( id, file_key, original_filename, content_type, size, created_at )
              VALUES (?, ?, 'profile.png', 'image/png', 1024, CURRENT_TIMESTAMP)
              """, profileImageId, profileImageKey);

        jdbcTemplate.update("""
              UPDATE users
              SET profile_image_id = ?
              WHERE id = ?
              """, profileImageId, counterpartId);
    }


}
