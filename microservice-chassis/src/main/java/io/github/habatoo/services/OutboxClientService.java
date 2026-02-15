package io.github.habatoo.services;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.enums.EventStatus;
import io.github.habatoo.dto.enums.EventType;
import io.github.habatoo.models.Outbox;
import io.github.habatoo.repositories.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис для реализации паттерна "Transactional Outbox".
 * <p>
 * Обеспечивает гарантированную доставку уведомлений путем их предварительного сохранения
 * в промежуточную таблицу базы данных. Это позволяет избежать потери событий при сбоях
 * внешнего сервиса уведомлений или брокера сообщений.
 *
 * @see <a href="https://microservices.io/patterns/data/transactional-outbox.html">Pattern: Transactional Outbox</a>
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxClientService {

    private static final String STATUS_NEW = "NEW";

    private static final String STATUS_PROCESSED = "PROCESSED";

    private static final String STATUS_FAILED = "FAILED";

    private final AtomicBoolean processing = new AtomicBoolean(false);

    private final OutboxRepository outboxRepository;

    private final KafkaNotificationPublisher kafkaNotificationPublisher;

    @Value("${spring.load_limit:${LOAD_LIMIT:100}}")
    private Integer loadLimit;

    /**
     * Сохраняет событие в таблицу Outbox для последующей асинхронной обработки.
     *
     * @param event объект события уведомления.
     * @return {@link Mono}, подтверждающий завершение операции сохранения.
     */
    public Mono<Void> saveEvent(NotificationEvent event) {
        Outbox outboxEntry = Outbox.builder()
                .eventType(event.getEventType().name())
                .payload(convertEventToMap(event))
                .status(STATUS_NEW)
                .isNew(true)
                .build();

        return outboxRepository.save(outboxEntry)
                .doOnSuccess(saved -> log.debug("Событие Outbox сохранено с ID: {}", saved.getId()))
                .doOnError(e -> log.error("Ошибка при сохранении в Outbox: {}", e.getMessage()))
                .contextWrite(ctx -> ctx)
                .then();
    }

    /**
     * Выполняет поиск необработанных событий и инициирует их отправку.
     * <p>
     * Метод обрабатывает каждое событие независимо: при успешной отправке помечает его
     * как обработанное, при возникновении исключения — как ошибочное.
     * </p>
     */
    public void processOutboxEvents() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        outboxRepository.findAllByStatus(STATUS_NEW)
                .limitRate(loadLimit)
                .flatMap(entity ->
                        processEvent(entity)
                                .then(markAsProcessed(entity.getId()))
                                .doOnSuccess(v -> log.info("Событие {} успешно обработано и помечено PROCESSED", entity.getId()))
                                .onErrorResume(e -> {
                                    log.error("Ошибка обработки события {}: {}", entity.getId(), e.getMessage());
                                    return markAsFailed(entity.getId());
                                })
                )
                .doFinally(signal -> processing.set(false))
                .subscribe();
    }

    /**
     * Удаляет старые записи из таблицы Outbox, находящиеся в конечных статусах.
     * <p>
     * Очистке подлежат записи, созданные более одного часа назад, чтобы предотвратить
     * бесконечный рост объема базы данных.
     * </p>
     */
    public void cleanupOldRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);

        outboxRepository.deleteByStatusAndCreatedAtBeforeCustom(STATUS_PROCESSED, threshold)
                .doOnSuccess(count -> log.info("Удалено PROCESSED записей: {}", count))
                .subscribe();

        outboxRepository.deleteByStatusAndCreatedAtBeforeCustom(STATUS_FAILED, threshold)
                .doOnSuccess(count -> log.info("Удалено FAILED записей: {}", count))
                .subscribe();
    }

    private Mono<Void> processEvent(Outbox entity) {
        NotificationEvent event = mapToEvent(entity);

        return kafkaNotificationPublisher.publish(event)
                .doOnError(e -> log.error("Kafka ошибка для события {}: {}",
                        entity.getId(), e.getMessage()));
    }

    private Map<String, Object> convertEventToMap(NotificationEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("username", event.getUsername());
        map.put("eventType", event.getEventType());
        map.put("status", event.getStatus());
        map.put("message", event.getMessage());
        map.put("sourceService", event.getSourceService());
        map.put("payload", event.getPayload() != null ? event.getPayload() : Map.of());
        return map;
    }

    private Mono<Void> markAsProcessed(UUID id) {
        return outboxRepository.updateStatus(id, STATUS_PROCESSED).then();
    }

    private Mono<Void> markAsFailed(UUID id) {
        return outboxRepository.updateStatus(id, STATUS_FAILED).then();
    }

    /**
     * Преобразование данных из БД (Map) обратно в DTO уведомления.
     */
    @SuppressWarnings("unchecked")
    private NotificationEvent mapToEvent(Outbox entity) {
        Map<String, Object> p = entity.getPayload();

        return NotificationEvent.builder()
                .username((String) p.get("username"))
                .eventType(EventType.valueOf((String) p.get("eventType")))
                .status(EventStatus.valueOf((String) p.get("status")))
                .message((String) p.get("message"))
                .sourceService((String) p.get("sourceService"))
                .payload((Map<String, Object>) p.get("payload"))
                .build();
    }
}
