package io.github.habatoo.services;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для реализации издателя уведомлений Kafka.
 * Тестирование проводится на моках без поднятия контекста Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Юнит-тесты KafkaNotificationPublisherImpl")
class KafkaNotificationPublisherTest {

    @Mock
    private KafkaSender<String, NotificationEvent> kafkaSender;

    @Mock
    private SenderResult<String> senderResult;

    private KafkaNotificationPublisher publisher;

    private final String testTopic = "test-topic";

    @BeforeEach
    void setUp() {
        publisher = new KafkaNotificationPublisher(kafkaSender, testTopic);
    }

    /**
     * Проверка успешной публикации события.
     */
    @Test
    @DisplayName("Успешная отправка события в Kafka")
    void shouldPublishSuccessfully() {
        NotificationEvent event = NotificationEvent.builder()
                .username("test_user")
                .eventType(EventType.TRANSFER)
                .payload(Map.of("content", "Test Message"))
                .build();

        when(kafkaSender.send(any(Publisher.class)))
                .thenReturn(Flux.just(senderResult));

        Mono<Void> result = publisher.publish(event);

        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<Publisher<SenderRecord<String, NotificationEvent, String>>> captor =
                ArgumentCaptor.forClass(Publisher.class);

        verify(kafkaSender).send(captor.capture());

        SenderRecord<String, NotificationEvent, String> capturedRecord =
                Flux.from(captor.getValue()).blockFirst();

        assertThat(capturedRecord).isNotNull();
        assertThat(capturedRecord.topic()).isEqualTo(testTopic);
        assertThat(capturedRecord.key()).isEqualTo("test_user");
        assertThat(capturedRecord.value()).isEqualTo(event);
    }

    /**
     * Проверка обработки ошибки при сбое в Kafka.
     */
    @Test
    @DisplayName("Обработка ошибки при сбое отправки в Kafka")
    void shouldHandleErrorWhenKafkaFails() {
        NotificationEvent event = NotificationEvent.builder()
                .username("test_user")
                .eventType(EventType.VALIDATION_ERROR)
                .payload(Map.of("content", "Error Message"))
                .build();
        RuntimeException kafkaException = new RuntimeException("Kafka connection lost");

        when(kafkaSender.send(any(Publisher.class)))
                .thenReturn(Flux.error(kafkaException));

        Mono<Void> result = publisher.publish(event);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Kafka connection lost"))
                .verify();

        verify(kafkaSender, times(1)).send(any(Publisher.class));
    }
}
