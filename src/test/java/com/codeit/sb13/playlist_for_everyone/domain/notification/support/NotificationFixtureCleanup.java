package com.codeit.sb13.playlist_for_everyone.domain.notification.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** 클래스가 공유하는 테스트 데이터는 추적한 알림 작업이 모두 종료된 뒤에 정리한다. */
public final class NotificationFixtureCleanup implements BeforeEachCallback {

    private boolean completionUnconfirmed;

    @Override
    public void beforeEach(ExtensionContext context) {
        assumeFalse(completionUnconfirmed, "이전 테스트의 알림 작업 종료를 확인하지 못했습니다.");
    }

    public void cleanUp(NotificationTaskTracker tasks, Executable deleteFixtures) {
        cleanUp(List.of(tasks), List.of(), deleteFixtures);
    }

    /**
     * 모든 노드의 추적기를 닫은 뒤 노드 같은 별도 리소스를 정리한다. DB와 이력은 작업의 종료가
     * 확인된 경우에만 삭제하며, 정리도 실패하면 최초 알림 작업 실패에 덧붙인다.
     */
    public void cleanUp(
            List<NotificationTaskTracker> trackers, List<Executable> closeResources, Executable deleteFixtures
    ) {
        List<AssertionError> taskFailures = new ArrayList<>();
        for (NotificationTaskTracker tracker : trackers) {
            try {
                tracker.close();
            } catch (AssertionError failure) {
                taskFailures.add(failure);
                if (failure instanceof NotificationTaskTracker.CompletionUnconfirmed) {
                    completionUnconfirmed = true;
                }
            }
        }

        List<Executable> cleanups = new ArrayList<>(closeResources);
        if (!completionUnconfirmed) {
            cleanups.add(deleteFixtures);
        }
        if (taskFailures.isEmpty()) {
            assertAll(cleanups);
            return;
        }

        AssertionError firstFailure = taskFailures.get(0);
        taskFailures.stream().skip(1).forEach(firstFailure::addSuppressed);
        try {
            assertAll(cleanups);
        } catch (AssertionError cleanupFailure) {
            firstFailure.addSuppressed(cleanupFailure);
        }
        throw firstFailure;
    }
}
