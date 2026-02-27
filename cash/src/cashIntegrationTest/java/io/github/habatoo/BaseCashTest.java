package io.github.habatoo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.habatoo.base.BaseTest;
import io.github.habatoo.dto.CashDto;
import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.dto.enums.OperationType;
import io.github.habatoo.models.Cash;
import io.github.habatoo.repositories.OperationsRepository;
import io.github.habatoo.services.CashService;
import io.github.habatoo.services.OutboxClientService;
import io.github.habatoo.services.OutboxService;
import io.micrometer.core.instrument.Counter;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.kafka.sender.KafkaSender;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@ActiveProfiles("test")
@SpringBootTest(
        classes = CashApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "chassis.security.enabled=false",
                "spring.kafka.admin.auto-create=false",
                "spring.kafka.bootstrap-servers=kafka:9092"
        }
)
@EnableAutoConfiguration(exclude = {
        ReactiveSecurityAutoConfiguration.class,
        ReactiveOAuth2ClientAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseCashTest extends BaseTest {

    protected static MockWebServer mockWebServer;

    @Autowired
    protected OutboxService outboxService;

    @MockitoBean
    protected OutboxClientService outboxClientService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected Counter cashSentFailureCounter;

    @MockitoBean
    protected ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    @MockitoBean
    protected ReactiveJwtDecoder reactiveJwtDecoder;

    @MockitoBean
    protected ReactiveClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    protected ReactiveOAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private KafkaSender<String, NotificationEvent> kafkaSender;

    @Autowired
    protected CashService cashService;

    @Autowired
    protected OperationsRepository operationsRepository;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(0);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @DynamicPropertySource
    static void specificProperties(DynamicPropertyRegistry registry) {
        registry.add("services.gateway.host", () -> "localhost");
        registry.add("services.gateway.port", () -> mockWebServer.getPort());
        registry.add("spring.cloud.loadbalancer.enabled", () -> "false");
        registry.add("spring.liquibase.change-log",
                () -> "classpath:db/changelog/cash/db.changelog-master.yaml");
        registry.add("spring.r2dbc.url", () -> "r2dbc:tc:postgresql:///test_db?TC_IMAGE_TAG=17-alpine");
    }

    protected Cash createCash(
            String name,
            BigDecimal amount,
            OperationType operationType) {
        return Cash.builder()
                .username(name)
                .amount(amount)
                .currency(Currency.RUB)
                .operationType(operationType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    protected CashDto createCashDto(
            OperationType operationType,
            BigDecimal value) {
        return CashDto.builder()
                .action(operationType)
                .currency(Currency.RUB)
                .accountId(UUID.randomUUID())
                .value(value)
                .build();
    }
}
