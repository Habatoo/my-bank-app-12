package io.github.habatoo.configurations;

import io.github.habatoo.repositories.OutboxRepository;
import io.github.habatoo.services.KafkaNotificationPublisher;
import io.github.habatoo.services.OutboxClientService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import reactor.kafka.sender.KafkaSender;

/**
 * Автоконфигурация сервисного слоя шасси микросервисов,
 * отвечает за регистрацию базовых инфраструктурных сервисов,
 * необходимых для реализации паттерна Outbox.
 */
@AutoConfiguration
@ConditionalOnClass({KafkaSender.class, OutboxRepository.class})
@ConditionalOnProperty(prefix = "chassis.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxChassisAutoConfiguration {

    /**
     * Создает бин сервиса управления Outbox-событиями.
     *
     * @param outboxRepository           репозиторий для хранения записей Outbox.
     * @param kafkaNotificationPublisher клиент для отправки накопленных уведомлений через Kafka.
     * @return настроенный экземпляр {@link OutboxClientService}.
     */
    @Bean
    @ConditionalOnBean({OutboxRepository.class, CircuitBreakerRegistry.class})
    @ConditionalOnMissingBean(OutboxClientService.class)
    public OutboxClientService outboxService(
            OutboxRepository outboxRepository,
            KafkaNotificationPublisher kafkaNotificationPublisher) {
        return new OutboxClientService(outboxRepository, kafkaNotificationPublisher);
    }
}
