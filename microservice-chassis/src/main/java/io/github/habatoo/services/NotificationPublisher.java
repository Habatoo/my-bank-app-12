package io.github.habatoo.services;

import io.github.habatoo.dto.NotificationEvent;
import reactor.core.publisher.Mono;

public interface NotificationPublisher {
    Mono<Void> publish(String targetTopic, NotificationEvent event);
    Mono<Void> publish(NotificationEvent event);
}
