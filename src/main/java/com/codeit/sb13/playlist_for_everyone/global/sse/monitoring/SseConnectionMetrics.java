package com.codeit.sb13.playlist_for_everyone.global.sse.monitoring;

import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseConnectionMetrics implements MeterBinder {

    private final SseEmitterRepository repository;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("everyone.sse.connections.active", repository, SseEmitterRepository::countActiveConnections)
                .register(registry);
    }
}
