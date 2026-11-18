package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 공통 생성 요청을 알림 저장 서비스에 전달하는 입력이다.
 *
 * <p>생성자는 제목 말줄임만 수행한다. null·공백 등의 Bean Validation은
 * Spring 빈을 통한 NotificationService.save 호출에서 {@code @Valid}로 적용된다.
 * 따라서 이 record를 직접 생성하는 것만으로 필수 값 검증이 실행되지는 않는다.
 */
public record NotificationCommand(
        @NotNull
        UUID receiverId,
        @NotBlank
        String title,
        @NotBlank
        String content,
        @NotNull
        NotificationLevel level
) {

    public NotificationCommand{
        title = truncateNotificationText(title);
    }

    public static NotificationCommand from(NotificationCreateRequestedEvent event) {
        return new NotificationCommand(event.receiverId(), event.title(), event.content(), event.level());
    }

    /**
     * 제목이 255 코드 포인트를 넘으면 앞 252개와 세 점을 남긴다. 본문에는 적용하지 않는다.
     * Java 문자열의 UTF-16 길이 대신 코드 포인트를 세어 이모지의 서로게이트 쌍을 보존한다.
     * null·공백은 원래 값으로 반환하여 이후 Bean Validation이 처리하도록 한다.
     */
    private String truncateNotificationText(String value){
        if (!StringUtils.hasText(value)) {
            return value;
        }

        int maxCodePoints = 255;
        String suffix = "...";

        int codePointCount = value.codePointCount(0, value.length());
        if(codePointCount <= maxCodePoints){
            return value;
        }

        int offsetByCodePoints = value.offsetByCodePoints(0, maxCodePoints - suffix.length());

        return value.substring(0,offsetByCodePoints) + suffix;
    }

}
