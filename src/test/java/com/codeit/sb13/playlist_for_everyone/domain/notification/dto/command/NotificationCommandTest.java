package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("알림 생성 요청의 제목 말줄임")
class NotificationCommandTest {

    @ParameterizedTest(name = "{0} {1}글자")
    @CsvSource({
            "가, 1", "가, 254", "가, 255",
            "😀, 1", "😀, 254", "😀, 255"
    })
    @DisplayName("제목이 255 코드 포인트 이하면 일반 문자와 이모지를 그대로 유지한다")
    void preservesTitlesWithinTheLimit(String character, int count) {
        String title = character.repeat(count);

        NotificationCommand command = new NotificationCommand(
                UUID.randomUUID(), title, "알림 내용", NotificationLevel.INFO);

        assertThat(command.title()).isEqualTo(title);
    }

    @ParameterizedTest(name = "{0} {1}글자")
    @CsvSource({"가, 256", "가, 300", "😀, 256", "😀, 300"})
    @DisplayName("제목이 255 코드 포인트를 넘으면 앞 252개와 말줄임표를 저장한다")
    void truncatesLongTitlesWithoutSplittingSurrogatePairs(String character, int count) {
        NotificationCommand command = new NotificationCommand(
                UUID.randomUUID(), character.repeat(count), "알림 내용", NotificationLevel.INFO);

        assertThat(command.title()).isEqualTo(character.repeat(252) + "...");
        assertThat(command.title().codePointCount(0, command.title().length())).isEqualTo(255);
    }

    @Test
    @DisplayName("말줄임 경계에 이모지가 있어도 서로게이트 쌍을 보존한다")
    void preservesAnEmojiAtTheTruncationBoundary() {
        String title = "가".repeat(251) + "😀" + "나".repeat(4);

        NotificationCommand command = new NotificationCommand(
                UUID.randomUUID(), title, "알림 내용", NotificationLevel.INFO);

        assertThat(command.title()).isEqualTo("가".repeat(251) + "😀...");
        assertThat(command.title().codePointCount(0, command.title().length())).isEqualTo(255);
    }
}
