package io.github.habatoo.configurations;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.enums.EventStatus;
import io.github.habatoo.dto.enums.EventType;
import io.github.habatoo.properties.ResilienceProperties;
import io.github.habatoo.services.NotificationPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Автоконфигурация механизмов отказоустойчивости (Resilience) для шасси.
 * Настраивает CircuitBreakerRegistry и логику уведомлений при смене состояний.
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceChassisAutoConfiguration {

    private final NotificationPublisher kafkaNotificationPublisher;

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @Value("${spring.kafka.resilience-topic:system-alerts}")
    private String resilienceTopic;

    /**
     * Создает и настраивает реестр CircuitBreaker.
     * При обновлении конфиг, этот бин будет пересоздан,
     * и все CircuitBreaker внутри него инициализируются заново с новыми проперти.
     */
    @Bean
    @RefreshScope
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties props) {
        CircuitBreakerConfig config = createBaseConfig(props);
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        setupStateTransitionObservation(registry);

        return registry;
    }

    /**
     * Настраивает кастомайзер для стандартного экземпляра CircuitBreaker.
     */
    @Bean
    public CircuitBreakerConfigCustomizer defaultCustomizer(ResilienceProperties props) {
        return obtainCircuitBreakerConfigCustomizer(props);
    }

    /**
     * Создает базовую конфигурацию на основе свойств.
     */
    private CircuitBreakerConfig createBaseConfig(ResilienceProperties props) {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(props.slidingWindowSize() != null ? props.slidingWindowSize().intValue() : 10)
                .failureRateThreshold(props.failureRateThreshold() != null ? props.failureRateThreshold().floatValue() : 50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(props.waitDurationInOpenState() != null ? props.waitDurationInOpenState() : 60))
                .permittedNumberOfCallsInHalfOpenState(props.permittedNumberOfCallsInHalfOpenState() != null ? props.permittedNumberOfCallsInHalfOpenState().intValue() : 10)
                .build();
    }

    /**
     * Настраивает подписку на события добавления новых CircuitBreaker для отслеживания смены состояний.
     */
    private void setupStateTransitionObservation(CircuitBreakerRegistry registry) {
        registry.getEventPublisher().onEntryAdded(entryAddedEvent -> {
            CircuitBreaker cb = entryAddedEvent.getAddedEntry();
            cb.getEventPublisher().onStateTransition(this::handleStateTransition);
        });
    }

    /**
     * Обрабатывает событие смены состояния: логирует и отправляет уведомление в сервис нотификаций.
     */
    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent stateEvent) {
        var transition = stateEvent.getStateTransition();
        var from = transition.getFromState();
        var to = transition.getToState();

        log.warn("CircuitBreaker '{}' transition: {} -> {}",
                stateEvent.getCircuitBreakerName(), from, to);

        sendNotification(stateEvent, from, to);
    }

    /**
     * Отправляет уведомление в сервис нотификаций.
     */
    private void sendNotification(
            CircuitBreakerOnStateTransitionEvent stateEvent,
            CircuitBreaker.State from,
            CircuitBreaker.State to) {

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .eventType(EventType.SYSTEM_ALERT)
                .status(EventStatus.FAILURE)
                .message(String.format("CircuitBreaker '%s' в сервисе '%s' изменил состояние: %s -> %s",
                        stateEvent.getCircuitBreakerName(), applicationName, from, to))
                .sourceService(applicationName)
                .payload(Map.of("fromState", from.name(), "toState", to.name()))
                .build();

        Mono.defer(() -> kafkaNotificationPublisher.publish(resilienceTopic, notificationEvent))
                .timeout(Duration.ofSeconds(2))
                .doOnError(e -> log.warn("Kafka недоступна. Лог CircuitBreaker сохранен только локально. Error: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * Возвращает кастомайзер конфигурации для конкретного экземпляра.
     */
    private CircuitBreakerConfigCustomizer obtainCircuitBreakerConfigCustomizer(ResilienceProperties props) {
        return CircuitBreakerConfigCustomizer.of(props.instanceName(), builder -> builder
                .slidingWindowSize(props.slidingWindowSize().intValue())
                .failureRateThreshold(props.failureRateThreshold().floatValue())
                .waitDurationInOpenState(Duration.ofSeconds(props.waitDurationInOpenState()))
                .permittedNumberOfCallsInHalfOpenState(props.permittedNumberOfCallsInHalfOpenState().intValue())
        );
    }
}
