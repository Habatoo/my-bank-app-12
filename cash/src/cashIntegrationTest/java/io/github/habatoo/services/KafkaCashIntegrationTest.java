package io.github.habatoo.services;

import io.github.habatoo.CashApplication;
import io.github.habatoo.controllers.CashController;
import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.enums.EventType;
import io.github.habatoo.repositories.OperationsRepository;
import io.github.habatoo.repositories.OutboxRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.test.StepVerifier;

@DirtiesContext
@SpringBootTest(
        classes = CashApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.liquibase.enabled=false",
                "spring.kafka.producer.properties.max.block.ms=500",
                "spring.kafka.producer.retries=0",
                "resilience4j.circuitbreaker.instances.kafka-publisher.slidingWindowSize=5",
                "resilience4j.circuitbreaker.instances.kafka-publisher.minimumNumberOfCalls=5",
                "resilience4j.circuitbreaker.instances.kafka-publisher.failureRateThreshold=50"
        }
)
@EnableAutoConfiguration(exclude = {
        ReactiveSecurityAutoConfiguration.class,
        ReactiveOAuth2ClientAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@EmbeddedKafka(
        topics = {KafkaCashIntegrationTest.TEST_TOPIC_NAME},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@ActiveProfiles("test")
class KafkaCashIntegrationTest {

    public static final String TEST_TOPIC_NAME = "test-topic";

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaNotificationPublisher publisher;

    @Autowired
    private CashService cashService;

    @MockitoBean
    private OutboxClientService outboxClientService;

    @MockitoBean
    protected ReactiveClientRegistrationRepository reactiveClientRegistrationRepository;

    @MockitoBean
    protected ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository;

    @MockitoBean
    protected ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService;

    @MockitoBean
    protected ReactiveJwtDecoder reactiveJwtDecoder;

    @MockitoBean(answers = Answers.RETURNS_SMART_NULLS)
    protected OutboxRepository outboxRepository;

    @MockitoBean
    private OperationsRepository operationsRepository;

    @MockitoBean
    private CashController cashController;

    @Test
    @DisplayName("publisher: Отправка event в Kafka")
    void testKafkaOutage() {
        NotificationEvent event = NotificationEvent.builder()
                .username("user_test")
                .eventType(EventType.DEPOSIT)
                .message("test")
                .build();

        StepVerifier.create(publisher.publish(event))
                .verifyComplete();
    }
}
