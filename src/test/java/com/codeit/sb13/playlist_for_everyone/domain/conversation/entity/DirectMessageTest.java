package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("다이렉트 메시지 도메인")
class DirectMessageTest {

    private final UUID userAId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID userBId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final String content = "안녕하세요.";

    @ParameterizedTest(name = "발신자가 참여자 A인가: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("어느 참여자가 보내든 입력한 정보로 미읽음 메시지를 생성한다.")
    void constructor_createsUnreadMessageForEitherDirection(boolean senderIsUserA){
        User userA = createUser(userAId, "참여자 A");
        User userB = createUser(userBId, "참여자 B");

        Conversation conversation = new Conversation(new ConversationCommand(userA, userB));

        User sender = senderIsUserA ? userA : userB;
        User receiver = senderIsUserA ? userB : userA;

        DirectMessageCommand command = new DirectMessageCommand(conversation, sender, receiver, content);

        DirectMessage message = new DirectMessage(command);

        assertThat(message.getConversation()).isSameAs(conversation);
        assertThat(message.getSender()).isSameAs(sender);
        assertThat(message.getReceiver()).isSameAs(receiver);
        assertThat(message.getContent()).isEqualTo(content);
        assertThat(message.isRead()).isFalse();
    }

    @ParameterizedTest(name = "누락된 필수 입력:{0}")
    @ValueSource(strings = {"command", "conversation", "sender", "receiver"})
    @DisplayName("필수 입력이 null이면 메시지 생성을 거절한다.")
    void constructor_rejectsMissingRequiredInput(String missingField){
        User sender = createUser(userAId, "발신자");
        User receiver = createUser(userBId, "수신자");

        Conversation conversation = new Conversation(new ConversationCommand(sender, receiver));

        assertThatThrownBy(() -> {
            DirectMessageCommand command = "command".equals(missingField) ? null
                    : new DirectMessageCommand(
                    "conversation".equals(missingField) ? null : conversation,
                    "sender".equals(missingField) ? null : sender,
                    "receiver".equals(missingField) ? null : receiver,
                    content);

            new DirectMessage(command);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "유효하지 않은 본문:[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("본문이 null이거나 빈 문자열 또는 공백뿐이면 메시지 생성을 거절한다.")
    void constructor_rejectsBlankContent(String content){
        User sender = createUser(userAId, "발신자");
        User receiver = createUser(userBId, "수신자");

        Conversation conversation = new Conversation(new ConversationCommand(sender, receiver));

        assertThatThrownBy(() -> {
            DirectMessageCommand command = new DirectMessageCommand(conversation, sender, receiver, content);
            new DirectMessage(command);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "발신자와 수신자가 같은 객체인가: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("객체가 달라도 발신자와 수신자의 사용자 ID가 같으면 메시지 생성을 거절한다.")
    void constructor_rejectsSameSenderAndReceiverId(boolean sameInstance){
        User userA = createUser(userAId, "참여자 A");
        User userB = createUser(userBId, "참여자 B");

        Conversation conversation = new Conversation(new ConversationCommand(userA, userB));

        User receiver = sameInstance ? userA : createUser(userAId, "동일 사용자의 다른 객체");

        assertThatThrownBy(() -> {
            DirectMessageCommand command = new DirectMessageCommand(conversation, userA, receiver, content);
            new DirectMessage(command);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "발신자 비참여={0}, 수신자 비참여={1}")
    @CsvSource({
            "true, false",
            "false, true",
            "true, true"
    })
    @DisplayName("발신자 또는 수신자가 대화방 참여자가 아니면 메시지 생성을 거절한다.")
    void constructor_rejectsNonParticipants(boolean senderIsOutsider, boolean receiverIsOutsider){
        User userA = createUser(userAId, "참여자 A");
        User userB = createUser(userBId, "참여자 B");

        Conversation conversation = new Conversation(new ConversationCommand(userA, userB));
        User sender = senderIsOutsider
                ? createUser(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "비참여 발신자")
                : userA;

        User receiver = receiverIsOutsider
                ? createUser(UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        "비참여 수신자")
                : userB;

        assertThatThrownBy(() -> {
            DirectMessageCommand command = new DirectMessageCommand(conversation, sender, receiver, content);
            new DirectMessage(command);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "발신자 참여자 A인가:{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("서로 다른 사용자 객체라도 ID가 대화 참여자와 일치하면 메시지를 생성한다.")
    void constructor_acceptsParticipantsWithMatchingIds(boolean senderIsUserA){
        User userA = createUser(userAId, "참여자 A");
        User userB = createUser(userBId, "참여자 B");

        Conversation conversation = new Conversation(new ConversationCommand(userA, userB));
        UUID senderId = senderIsUserA ? userAId : userBId;
        UUID receiverId = senderIsUserA ? userBId : userAId;

        User sender = createUser(UUID.fromString(senderId.toString()), "별도로 조회한 발신자");
        User receiver = createUser(UUID.fromString(receiverId.toString()), "별도로 조회한 수신자");

        DirectMessageCommand command = new DirectMessageCommand(conversation, sender, receiver, content);
        DirectMessage message = new DirectMessage(command);

        assertThat(message.getSender().getId()).isEqualTo(senderId);
        assertThat(message.getReceiver().getId()).isEqualTo(receiverId);
    }

    private User createUser(UUID id, String name) {
        User user = User.builder()
                .email(id + "@test.com")
                .name(name)
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user,
                "id", id);
        return user;
    }

}
