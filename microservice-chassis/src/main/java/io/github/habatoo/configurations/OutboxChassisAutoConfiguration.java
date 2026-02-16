package io.github.habatoo.configurations;

import io.github.habatoo.repositories.OutboxRepository;
import io.github.habatoo.services.NotificationPublisher;
import io.github.habatoo.services.OutboxClientService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Автоконфигурация сервисного слоя шасси микросервисов,
 * отвечает за регистрацию базовых инфраструктурных сервисов,
 * необходимых для реализации паттерна Outbox.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "chassis.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxChassisAutoConfiguration {

    /**
     * Создает бин сервиса управления Outbox-событиями.
     *
     * @param outboxRepository      репозиторий для хранения записей Outbox.
     * @param notificationPublisher клиент для отправки накопленных уведомлений через Kafka.
     * @return настроенный экземпляр {@link OutboxClientService}.
     */
    @Bean
    public OutboxClientService outboxService(
            OutboxRepository outboxRepository,
            NotificationPublisher notificationPublisher) {
        return new OutboxClientService(outboxRepository, notificationPublisher);
    }
}
