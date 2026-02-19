package io.github.habatoo.services;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.enums.EventStatus;
import io.github.habatoo.dto.enums.EventType;
import io.github.habatoo.models.Outbox;
import io.github.habatoo.repositories.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link OutboxClientService} с использованием моков.
 * Проверяют сохранение, обработку и механизмы восстановления после ошибок.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Юнит-тесты сервиса OutboxClientService")
class OutboxClientServiceTest {

    private final UUID entityId = UUID.randomUUID();

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaNotificationPublisher kafkaNotificationPublisher;

    @InjectMocks
    private OutboxClientService outboxClientService;

    private NotificationEvent testEvent;

    private Outbox testEntity;

    @BeforeEach
    void setUp() {
        testEvent = NotificationEvent.builder()
                .username("test_user")
                .eventType(EventType.REGISTRATION)
                .status(EventStatus.SUCCESS)
                .message("Welcome!")
                .sourceService("bank-service")
                .payload(Map.of("key", "value"))
                .build();

        testEntity = Outbox.builder()
                .id(entityId)
                .eventType(EventType.REGISTRATION.name())
                .status("NEW")
                .payload(Map.of(
                        "username", "test_user",
                        "eventType", "REGISTRATION",
                        "status", "SUCCESS",
                        "message", "Welcome!",
                        "sourceService", "bank-service",
                        "payload", Map.of("key", "value")
                ))
                .build();

        ReflectionTestUtils.setField(outboxClientService, "loadLimit", 100);
    }

    @Test
    @DisplayName("Успешное сохранение события в Outbox")
    void saveEventSuccessTest() {
        when(outboxRepository.save(any(Outbox.class))).thenReturn(Mono.just(testEntity));

        StepVerifier.create(outboxClientService.saveEvent(testEvent))
                .verifyComplete();

        verify(outboxRepository, times(1)).save(any(Outbox.class));
    }

    @Test
    @DisplayName("Успешная обработка событий: NEW -> PROCESSED")
    void processOutboxEventsSuccessTest() {
        when(outboxRepository.findAllByStatus("NEW")).thenReturn(Flux.just(testEntity));
        when(kafkaNotificationPublisher.publish(any(NotificationEvent.class))).thenReturn(Mono.empty());
        when(outboxRepository.updateStatus(entityId, "PROCESSED")).thenReturn(Mono.empty());

        outboxClientService.processOutboxEvents();

        verify(kafkaNotificationPublisher, timeout(1000)).publish(any(NotificationEvent.class));
        verify(outboxRepository, timeout(1000)).updateStatus(entityId, "PROCESSED");
    }

    @Test
    @DisplayName("Ошибка обработки события: NEW -> FAILED")
    void processOutboxEventsFailureShouldMarkAsFailedTest() {
        lenient().when(outboxRepository.updateStatus(any(), anyString())).thenReturn(Mono.empty());

        when(outboxRepository.findAllByStatus("NEW")).thenReturn(Flux.just(testEntity));
        when(kafkaNotificationPublisher.publish(any(NotificationEvent.class)))
                .thenReturn(Mono.error(new RuntimeException("Network error")));

        outboxClientService.processOutboxEvents();

        verify(outboxRepository, timeout(1000)).updateStatus(entityId, "FAILED");
    }

    @Test
    @DisplayName("Очистка старых записей вызывает репозиторий")
    void cleanupOldRecordsSuccessTest() {
        when(outboxRepository
                .deleteByStatusAndCreatedAtBeforeCustom(anyString(), any())).thenReturn(Mono.just(5L));

        outboxClientService.cleanupOldRecords();

        verify(outboxRepository, times(2))
                .deleteByStatusAndCreatedAtBeforeCustom(anyString(), any());
    }
}
