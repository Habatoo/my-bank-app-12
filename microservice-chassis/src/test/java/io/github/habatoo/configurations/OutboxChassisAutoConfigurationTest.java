package io.github.habatoo.configurations;

import io.github.habatoo.repositories.OutboxRepository;
import io.github.habatoo.services.KafkaNotificationPublisher;
import io.github.habatoo.services.OutboxClientService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Тесты для проверки корректности загрузки автоконфигурации шасси.
 * Проверяют регистрацию бинов в реактивном приложении.
 */
@DisplayName("Юнит-тесты для OutboxChassisAutoConfiguration")
class OutboxChassisAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxChassisAutoConfiguration.class))
            .withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/jwks")
            .withBean(ReactiveJwtDecoder.class, () -> mock(ReactiveJwtDecoder.class))
            .withBean(ReactiveClientRegistrationRepository.class, () -> mock(ReactiveClientRegistrationRepository.class))
            .withBean(ServerOAuth2AuthorizedClientRepository.class, () -> mock(ServerOAuth2AuthorizedClientRepository.class));

    /**
     * Тест: Успешная регистрация при наличии всех зависимостей
     */
    @Test
    @DisplayName("Проверка: регистрация бина OutboxClientService при наличии всех зависимостей")
    void shouldRegisterOutboxClientService() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxClientService.class);
                });
    }

    /**
     * Тест: Бины НЕ должны создаваться, если свойство enabled=false
     */
    @Test
    @DisplayName("Проверка: бин НЕ регистрируется, если chassis.outbox.enabled=false")
    void shouldNotRegisterWhenDisabled() {
        contextRunner
                .withUserConfiguration(MockConfig.class)
                .withPropertyValues("chassis.outbox.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxClientService.class);
                });
    }

    static class MockConfig {
        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
            return mock(CircuitBreakerRegistry.class);
        }

        @Bean
        public OutboxRepository outboxRepository() {
            return mock(OutboxRepository.class);
        }

        @Bean
        public KafkaNotificationPublisher kafkaNotificationPublisher() {
            return mock(KafkaNotificationPublisher.class);
        }
    }
}
