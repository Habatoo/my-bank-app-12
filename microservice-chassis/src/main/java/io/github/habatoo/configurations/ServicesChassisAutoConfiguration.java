package io.github.habatoo.configurations;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.services.KafkaNotificationPublisher;
import io.github.habatoo.services.NoOpNotificationPublisher;
import io.github.habatoo.services.NotificationPublisher;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Автоконфигурация сервисного слоя шасси микросервисов,
 * отвечает за регистрацию базовых инфраструктурных сервисов,
 * необходимых для работы системы уведомлений.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaSender.class)
public class ServicesChassisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SenderOptions.class)
    @ConditionalOnProperty(prefix = "chassis.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SenderOptions<String, NotificationEvent> senderOptions(KafkaProperties kafkaProperties) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);


        return SenderOptions.create(props);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean(KafkaSender.class)
    @ConditionalOnProperty(prefix = "chassis.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaSender<String, NotificationEvent> kafkaSender(
            SenderOptions<String, NotificationEvent> senderOptions) {
        return KafkaSender.create(senderOptions);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "spring.kafka.topics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    @ConditionalOnProperty(prefix = "chassis.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin.NewTopics topics(
            @Value("${spring.kafka.topics.partitions:3}") int partitions,
            @Value("${spring.kafka.topics.replicas:1}") short replicas,
            @Value("${spring.kafka.topics.resilience-name:system-alerts}") String resilienceTopic
    ) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("account-notifications").partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name("cash-notifications").partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name("transfer-notifications").partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(resilienceTopic).partitions(partitions).replicas(replicas).build()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "chassis.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public NotificationPublisher kafkaNotificationPublisher(
            @Lazy KafkaSender<String, NotificationEvent> kafkaSender,
            @Value("${spring.kafka.topics.topic:${KAFKA_TOPIC:chassis}}") String topic) {
        return new KafkaNotificationPublisher(kafkaSender, topic);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationPublisher.class)
    public NotificationPublisher noOpPublisher() {
        return new NoOpNotificationPublisher();
    }
}
