package io.github.habatoo.services;

import io.github.habatoo.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Клиент заглушка при отсутствии notifications через Kafka.
 */
@Slf4j
public class NoOpNotificationPublisher implements NotificationPublisher {

    @Override
    public Mono<Void> publish(String targetTopic, NotificationEvent event) {
        return publish(event);
    }

    @Override
    public Mono<Void> publish(NotificationEvent event) {
        log.debug("Kafka disabled. Skipping notification for user={}", event.getUsername());
        return Mono.empty();
    }
}
