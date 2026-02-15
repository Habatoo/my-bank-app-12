package io.github.habatoo.configurations;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.services.KafkaNotificationPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит тесты бина ResilienceChassisAutoConfiguration и связанных бинов.
 */
@DisplayName("Юнит-тесты для ResilienceChassisAutoConfiguration")
class ResilienceChassisAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ResilienceChassisAutoConfiguration.class,
                    RefreshAutoConfiguration.class
            ))
            .withBean(KafkaNotificationPublisher.class, () -> {
                KafkaNotificationPublisher mock = mock(KafkaNotificationPublisher.class);
                when(mock.publish(anyString(), any(NotificationEvent.class))).thenReturn(Mono.empty());
                return mock;
            });

    @Test
    @DisplayName("Должен создавать бины Resilience с параметрами из свойств")
    void shouldCreateResilienceBeans() {
        contextRunner.withPropertyValues(
                "resilience.instanceName=testService",
                "resilience.slidingWindowSize=10",
                "resilience.failureRateThreshold=50",
                "resilience.waitDurationInOpenState=30",
                "resilience.permittedNumberOfCallsInHalfOpenState=3"
        ).run(context -> {
            assertThat(context).hasBean("circuitBreakerRegistry");
            assertThat(context).hasSingleBean(CircuitBreakerConfigCustomizer.class);

            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            var config = registry.getDefaultConfig();

            assertThat(config.getSlidingWindowSize()).isEqualTo(10);
            assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        });
    }

    @Test
    @DisplayName("Должен отправлять уведомление при переходе CircuitBreaker в состояние OPEN")
    void shouldSendNotificationOnStateTransition() {
        contextRunner.withPropertyValues(
                "spring.application.name=banking-service",
                "resilience.instanceName=payment-cb"
        ).run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            KafkaNotificationPublisher notificationService = context.getBean(KafkaNotificationPublisher.class);

            CircuitBreaker circuitBreaker = registry.circuitBreaker("payment-cb");

            circuitBreaker.transitionToOpenState();

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationService, timeout(2000)).publish(topicCaptor.capture(), eventCaptor.capture());

            String captureTopic = topicCaptor.getValue();
            assertThat(captureTopic).isEqualTo("system-alerts");
            NotificationEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getSourceService()).isEqualTo("banking-service");
            assertThat(capturedEvent.getPayload()).containsEntry("toState", "OPEN");
        });
    }

    @Test
    @DisplayName("Должен использовать дефолтное имя приложения, если оно не задано")
    void shouldHandleDefaultAppNameInNotifications() {
        contextRunner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            KafkaNotificationPublisher notificationService = context.getBean(KafkaNotificationPublisher.class);

            CircuitBreaker cb = registry.circuitBreaker("default-cb");
            cb.transitionToDisabledState();

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationService, timeout(2000)).publish(topicCaptor.capture(), eventCaptor.capture());

            assertThat(eventCaptor.getValue().getSourceService()).isEqualTo("unknown-service");
        });
    }
}
