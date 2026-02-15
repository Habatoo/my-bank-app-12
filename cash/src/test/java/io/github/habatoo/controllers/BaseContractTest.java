package io.github.habatoo.controllers;

import io.github.habatoo.CashApplication;
import io.github.habatoo.configurations.TestSecurityConfiguration;
import io.github.habatoo.dto.CashDto;
import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.OperationResultDto;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.dto.enums.OperationType;
import io.github.habatoo.repositories.OperationsRepository;
import io.github.habatoo.services.CashService;
import io.github.habatoo.services.OutboxClientService;
import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {
                CashApplication.class,
                TestSecurityConfiguration.class
        },
        properties = {
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.liquibase.enabled=false",
                "spring.security.enabled=false",
                "spring.kafka.admin.auto-create=false",
                "spring.kafka.bootstrap-servers=kafka:9092",
                "spring.security.oauth2.client.registration.keycloak.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration,org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
        }
)
@EnableAutoConfiguration(exclude = {
        ReactiveSecurityAutoConfiguration.class,
        ReactiveOAuth2ClientAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        R2dbcAutoConfiguration.class,
        R2dbcTransactionManagerAutoConfiguration.class
})
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@AutoConfigureMessageVerifier
@AutoConfigureWebTestClient
public abstract class BaseContractTest {

    @MockitoBean
    private CashService cashService;

    @MockitoBean
    private ReactiveClientRegistrationRepository reactiveClientRegistrationRepository;

    @MockitoBean
    private ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService;

    @MockitoBean
    private ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository;

    @MockitoBean
    private OperationsRepository operationsRepository;

    @MockitoBean
    private OutboxClientService outboxClientService;

    @MockitoBean
    private KafkaSender<String, NotificationEvent> kafkaSender;

    @Autowired
    protected ApplicationContext context;

    @Autowired
    protected WebTestClient webTestClient;

    @BeforeEach
    void setup() {
        setupServiceMocks();
        RestAssuredWebTestClient.webTestClient(webTestClient);
    }

    private void setupServiceMocks() {
        String mockSubject = "367c3cf3-af3e-44af-ab7b-6d2034c6fca6";
        UUID userId = UUID.fromString(mockSubject);
        LocalDateTime localDateTime = LocalDateTime.of(2026, 2, 2, 10, 10, 10);

        CashDto depositResponseData = CashDto.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .value(new BigDecimal("100.00"))
                .currency(Currency.RUB)
                .accountId(UUID.randomUUID())
                .action(OperationType.PUT)
                .version(1L)
                .createdAt(localDateTime)
                .build();

        OperationResultDto<CashDto> depositResponse = OperationResultDto.<CashDto>builder()
                .success(true)
                .message("Операция успешно проведена и сохранена")
                .data(depositResponseData)
                .build();

        CashDto withdrawResponseData = CashDto.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .value(new BigDecimal("50.00"))
                .currency(Currency.RUB)
                .action(OperationType.GET)
                .version(2L)
                .createdAt(localDateTime)
                .build();

        OperationResultDto<CashDto> withdrawResponse = OperationResultDto.<CashDto>builder()
                .success(true)
                .message("Операция успешно проведена и сохранена")
                .data(withdrawResponseData)
                .build();

        OperationResultDto<CashDto> invalidActionError = OperationResultDto.<CashDto>builder()
                .success(false)
                .message("Неверный формат параметров: No enum constant io.github.habatoo.dto.enums.OperationType.INVALID_ACTION")
                .build();

        when(cashService.processCashOperation(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    BigDecimal value = invocation.getArgument(0);
                    String action = invocation.getArgument(1);
                    String currency = invocation.getArgument(2);
                    Jwt jwt = invocation.getArgument(3);

                    String username = jwt.getClaimAsString("preferred_username");

                    if ("user1".equals(username) && "RUB".equalsIgnoreCase(currency)) {
                        if ("PUT".equalsIgnoreCase(action) && value.compareTo(new BigDecimal("100.00")) == 0) {
                            return Mono.just(depositResponse);
                        }
                        if ("GET".equalsIgnoreCase(action) && value.compareTo(new BigDecimal("50.00")) == 0) {
                            return Mono.just(withdrawResponse);
                        }
                    }

                    if ("INVALID_ACTION".equals(action)) {
                        return Mono.just(invalidActionError);
                    }

                    return Mono.just(OperationResultDto.<CashDto>builder()
                            .success(false)
                            .message("No mock match")
                            .build());
                });

        when(operationsRepository.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(outboxClientService.saveEvent(any())).thenReturn(Mono.empty());
    }
}
