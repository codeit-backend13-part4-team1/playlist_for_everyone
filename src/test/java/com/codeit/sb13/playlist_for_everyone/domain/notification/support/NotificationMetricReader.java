package com.codeit.sb13.playlist_for_everyone.domain.notification.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public final class NotificationMetricReader {

    private NotificationMetricReader() {
    }

    public static double savedCount(MeterRegistry registry) {
        return counterCount(registry, "everyone.notification.saved");
    }

    public static double ssePublishedCount(MeterRegistry registry) {
        return counterCount(registry, "everyone.notification.sse.published");
    }

    public static double failureCount(MeterRegistry registry, String stage) {
        return counterCount(registry, "everyone.notification.failures", "stage", stage);
    }

    public static double publishAttempts(MeterRegistry registry, String source) {
        return counterCount(registry, "everyone.notification.publish.attempts", "source", source);
    }

    public static long processingCount(MeterRegistry registry, String source) {
        return registry.find("everyone.notification.processing.duration")
                .tag("source", source)
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
    }

    private static double counterCount(MeterRegistry registry, String name, String... tags) {
        return registry.find(name)
                .tags(tags)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
