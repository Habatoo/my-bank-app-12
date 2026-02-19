package io.github.habatoo.configurations;

import io.github.habatoo.services.KafkaNotificationPublisher;
import io.github.habatoo.services.NoOpNotificationPublisher;
import io.github.habatoo.services.NotificationPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaAdmin;
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
            ));

    static class MockConfig {
        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
            return mock(CircuitBreakerRegistry.class);
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
        contextRunner
                .withPropertyValues(
                        "chassis.kafka.enabled=true",
                        "spring.kafka.topics.enabled=true",
                        "spring.kafka.bootstrap-servers=localhost:9092"
                )
                .withUserConfiguration(MockConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaAdmin.NewTopics.class);
                });
    }

    /**
     * Проверка регистрации издателя уведомлений Kafka.
     */
    @Test
    @DisplayName("Должен создать KafkaNotificationPublisher, когда chassis.kafka.enabled=true")
    void shouldRegisterKafkaNotificationPublisher() {
        contextRunner
                .withPropertyValues(
                        "chassis.kafka.enabled=true",
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "spring.kafka.topics.enabled=true"
                )
                .withUserConfiguration(MockConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaNotificationPublisher.class);
                    assertThat(context).doesNotHaveBean(NoOpNotificationPublisher.class);
                    assertThat(context).hasSingleBean(SenderOptions.class);
                });
    }

    /**
     * Проверка регистрации издателя уведомлений без Kafka.
     */
    @Test
    @DisplayName("Должен создать NoOpNotificationPublisher, когда chassis.kafka.enabled=false")
    void shouldRegisterNoOpPublisherWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "chassis.kafka.enabled=false"
                )
                .withUserConfiguration(MockConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(NotificationPublisher.class);
                    assertThat(context.getBean(NotificationPublisher.class))
                            .isInstanceOf(NoOpNotificationPublisher.class);

                    assertThat(context).doesNotHaveBean(KafkaNotificationPublisher.class);
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
