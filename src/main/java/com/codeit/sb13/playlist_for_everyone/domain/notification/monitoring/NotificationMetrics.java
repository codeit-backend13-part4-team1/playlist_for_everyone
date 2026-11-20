package com.codeit.sb13.playlist_for_everyone.domain.notification.monitoring;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationMetrics {

    private static final String METRIC_PREFIX = "everyone.notification";
    private static final String FAILURES = METRIC_PREFIX + ".failures";

    private final MeterRegistry meterRegistry;

    public void recordSaved(){
        meterRegistry.counter(METRIC_PREFIX + ".saved").increment();
    }

    public void recordSsePublished(){
        meterRegistry.counter(METRIC_PREFIX + ".sse.published").increment();
    }

    public void recordSseFailure(){
        meterRegistry.counter(FAILURES, "stage", "sse").increment();
    }

    public void recordSaveFailure(){
        meterRegistry.counter(FAILURES, "stage", "save").increment();
    }

    public void recordLookupFailure(){
        meterRegistry.counter(FAILURES, "stage", "lookup").increment();
    }

    public void recordPublishFailure(){
        meterRegistry.counter(FAILURES, "stage", "publish").increment();
    }

    public void recordPublishAttempt(NotificationSource source) {
        meterRegistry.counter(METRIC_PREFIX + ".publish.attempts", "source", source.tagValue()).increment();
    }

    public Timer.Sample startProcessing() {
        return Timer.start(meterRegistry);
    }

    public void recordProcessing(NotificationSource source, Timer.Sample sample) {
        sample.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.duration", "source", source.tagValue()));
    }
}
