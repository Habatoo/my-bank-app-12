package io.github.habatoo.services;

import io.github.habatoo.dto.NotificationEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/**
 * Клиент для отправки notifications через модульу ведомлений.
 * Обеспечиват единообразную отправвку уведомлений всеми сервисами через сервис уведомлений.
 */
@Slf4j
public class KafkaNotificationPublisher {

    private final KafkaSender<String, NotificationEvent> kafkaSender;

    private final String topic;

    public KafkaNotificationPublisher(
            KafkaSender<String, NotificationEvent> kafkaSender,
            @Value("${spring.kafka.topics.topic:${KAFKA_TOPIC:chassis}}") String topic) {
        this.kafkaSender = kafkaSender;
        this.topic = topic;
    }

    /**
     * Метод для вызова отправки в сервис уведомлений.
     *
     * @param targetTopic топик для отправки.
     * @param event       единое событие уведомления для отправки.
     * @return асинхронный объект результата уведомлений.
     */
    @CircuitBreaker(name = "kafka-publisher")
    public Mono<Void> publish(String targetTopic, NotificationEvent event) {
        String activeTopic = (targetTopic != null) ? targetTopic : this.topic;
        String key = event.getUsername();

        SenderRecord<String, NotificationEvent, String> record =
                SenderRecord.create(new ProducerRecord<>(activeTopic, key, event), key);

        return kafkaSender.send(Mono.just(record))
                .next()
                .doOnError(e -> log.error("Kafka publish to {} failed", activeTopic, e))
                .then();
    }

    /**
     * Метод для вызова отправки в сервис уведомлений.
     *
     * @param event единое событие уведомления для отправки.
     * @return асинхронный объект результата уведомлений.
     */
    public Mono<Void> publish(NotificationEvent event) {
        return publish(this.topic, event);
    }
}
