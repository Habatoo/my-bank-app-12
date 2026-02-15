package io.github.habatoo.configurations;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.services.KafkaNotificationPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaAdmin;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Тесты для проверки корректности загрузки автоконфигурации шасси.
 * Проверяют регистрацию бинов в реактивном приложении.
 */
@DisplayName("Юнит-тесты для ServicesChassisAutoConfiguration")
class ServicesChassisAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ServicesChassisAutoConfiguration.class,
                    KafkaAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.main.allow-bean-definition-overriding=true",
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "spring.kafka.topics.topic=test-topic",
                    "spring.kafka.topics.enabled=true"
            )
            .withUserConfiguration(MockConfig.class);

    static class MockConfig {
        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
            return mock(CircuitBreakerRegistry.class);
        }

        @Bean
        @Primary
        public KafkaAdmin kafkaAdmin() {
            return mock(KafkaAdmin.class);
        }

        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        public KafkaSender<String, NotificationEvent> kafkaSender() {
            return mock(KafkaSender.class);
        }
    }

    /**
     * Проверка создания самой автоконфигурации.
     */
    @Test
    @DisplayName("Проверка: бин автоконфигурации присутствует в контексте")
    void shouldCreateAutoConfigBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ServicesChassisAutoConfiguration.class);
        });
    }

    /**
     * Проверка регистрации создания настройки Kafka.
     */
    @Test
    @DisplayName("Проверка: регистрация бина SenderOptions")
    void shouldRegisterSenderOptions() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SenderOptions.class);
        });
    }

    /**
     * Проверка регистрации создания топиков Kafka.
     */
    @Test
    @DisplayName("Проверка: регистрация бина KafkaAdmin.NewTopics")
    void shouldRegisterKafkaAdminNewTopics() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KafkaAdmin.NewTopics.class);
        });
    }

    /**
     * Проверка регистрации издателя уведомлений Kafka.
     */
    @Test
    @DisplayName("Проверка: регистрация бина KafkaNotificationPublisher")
    void shouldRegisterKafkaNotificationPublisher() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KafkaNotificationPublisher.class);
        });
    }

    /**
     * Проверка отсутствия бинов при отсутствии конфигурации.
     */
    @Test
    @DisplayName("Проверка: отсутствие бинов шасси в чистом контексте")
    void shouldNotContainChassisBeansInEmptyContext() {
        new ApplicationContextRunner()
                .run(context -> {
                    assertThat(context).doesNotHaveBean(KafkaNotificationPublisher.class);
                    assertThat(context).doesNotHaveBean(SenderOptions.class);
                });
    }
}
