package com.codeit.sb13.playlist_for_everyone.domain.notification.controller;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.WebMvcConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("알림 API 요청 검증")
public class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    String cursor = "2026-10-23T10:30:00.123456";

    @Test
    @DisplayName("idAfter가 없어도 잘못된 커서는 400을 반환한다.")
    void rejectsInvalidCursorWithoutIdAfter() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("cursor", "invalid-date")
                .param("limit","20")
                .param("sortDirection","DESCENDING")
                .param("sortBy", "createdAt")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("커서를 생략하면 첫 페이지 조회 요청을 허용하고 200을 반환한다.")
    void returnsOkWhenCursorIsOmitted() throws Exception {
        CursorResponse<NotificationDto> response = new CursorResponse<>(
                List.of(),
                null,
                null,
                false,
                0L,
                "createdAt",
                SortDirection.DESCENDING
        );

        when(notificationService.getNotifications(any()))
                .thenReturn(response);


        mockMvc.perform(get("/api/notifications")
                .param("limit", "20")
                .param("sortDirection", "DESCENDING")
                .param("sortBy", "createdAt")
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0));

        verify(notificationService).getNotifications(argThat(
                condition -> condition.cursor() == null && condition.idAfter() == null)
        );

    }

    @Test
    @DisplayName("정상 커서와 idAfter를 전달하면 200을 반환하고 조회 조건에 반영한다")
    void acceptsValidCursorAndIdAfter() throws Exception {
        UUID idAfter = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        CursorResponse<NotificationDto> response = new CursorResponse<>(
                List.of(),
                null,
                null,
                false,
                0L,
                "createdAt",
                SortDirection.DESCENDING
        );

        when(notificationService.getNotifications(any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/notifications")
                .param("cursor",cursor)
                .param("idAfter", idAfter.toString())
                .param("limit","20")
                .param("sortDirection","DESCENDING")
                .param("sortBy", "createdAt")
        ).andExpect(status().isOk());

        verify(notificationService).getNotifications(
                argThat(condition ->
                        cursor.equals(condition.cursor()) && idAfter.equals(condition.idAfter())
                ));

    }

    @Test
    @DisplayName("idAfter가 UUID 형식이 아니면 400을 반환한다.")
    void rejectsInvalidIdAfter() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("cursor",cursor)
                .param("idAfter", "invalid-uuid")
                .param("limit","20")
                .param("sortDirection","DESCENDING")
                .param("sortBy", "createdAt")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }


    @Test
    @DisplayName("필수 파라미터 limit을 생략하면 400을 반환한다.")
    void rejectsMissingLimit() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("sortDirection","DESCENDING")
                .param("sortBy", "createdAt")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @ParameterizedTest(name = "limit={0}이면 400을 반환한다.")
    @ValueSource(ints={0,-1})
    @DisplayName("limit이 0 또는 음수이면 400을 반환한다.")
    void rejectsNonPositiveLimit(int limit) throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("limit", String.valueOf(limit))
                .param("sortDirection","DESCENDING")
                .param("sortBy", "createdAt")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("limit에 숫자가 아닌 문자열을 전달하면 400을 반환한다.")
    void rejectsNonNumericLimit() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("limit", "abc")
                .param("sortDirection","DESCENDING")
                .param("sortBy", "createdAt")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("필수 파라미터 sortDirection을 생략하면 400을 반환한다.")
    void rejectsMissingSortDirection() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("limit","20")
                .param("sortBy", "createdAt")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("지원하지 않는 sortDirection을 전달하면 400을 반환한다.")
    void rejectsInvalidSortDirection() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("limit","20")
                .param("sortDirection","INVALID")
                .param("sortBy", "createdAt")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("필수 파라미터 sortBy를 생략하면 400을 반환한다.")
    void rejectsMissingSortBy() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("limit", "20")
                .param("sortDirection", "DESCENDING")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("지원 하지 않는 sortBy를 전달하면 400을 반환한다.")
    void rejectsInvalidSortBy() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("limit", "20")
                .param("sortDirection", "DESCENDING")
                .param("sortBy", "title")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("정상 알림 ID로 삭제를 요청하면 204와 빈 본문을 반환한다.")
    void returnsNoContentWhenDeletingNotification() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(notificationService).confirm(notificationId, null);
    }

    @Test
    @DisplayName("잘못된 알림 ID로 삭제를 요청하면 400과 공통 오류 응답을 반환한다.")
    void rejectsInvalidNotificationIdWhenDeleting() throws Exception {

        mockMvc.perform(delete("/api/notifications/{notificationId}", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("MethodArgumentTypeMismatchException"))
                .andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty())
        ;
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("조회 중 예상하지 못한 예외가 발생하면 500과 공통 오류 응답을 반환한다.")
    void returnsCommonErrorWhenNotificationSearchFails() throws Exception {
        String internalMessage = "DB connection failed";

        when(notificationService.getNotifications(any()))
                .thenThrow(new IllegalStateException(internalMessage));

        mockMvc.perform(get("/api/notifications")
                        .param("limit","20")
                        .param("sortDirection","DESCENDING")
                        .param("sortBy", "createdAt")
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.exceptionName").value("IllegalStateException"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류"))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString(internalMessage))))
        ;
        verify(notificationService).getNotifications(any());
    }


}
