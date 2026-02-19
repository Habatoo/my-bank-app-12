package io.github.habatoo.controllers;

import io.github.habatoo.AccountApplication;
import io.github.habatoo.configurations.TestSecurityConfiguration;
import io.github.habatoo.dto.*;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.repositories.AccountRepository;
import io.github.habatoo.repositories.OutboxRepository;
import io.github.habatoo.repositories.UserRepository;
import io.github.habatoo.services.AccountService;
import io.github.habatoo.services.OutboxService;
import io.github.habatoo.services.UserService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {
                AccountApplication.class,
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
public abstract class BaseAccountContractTest {

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OutboxService outboxService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private OutboxRepository outboxRepository;

    @MockitoBean
    private KafkaSender<String, NotificationEvent> kafkaSender;

    @MockitoBean
    private ReactiveClientRegistrationRepository reactiveClientRegistrationRepository;

    @MockitoBean
    private ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository;

    @MockitoBean
    private ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    protected WebTestClient webTestClient;

    @BeforeEach
    void setup() {
        setupServiceMocks();
        RestAssuredWebTestClient.webTestClient(webTestClient);
    }

    private void setupServiceMocks() {
        UserProfileResponseDto mockDtoUser = new UserProfileResponseDto(
                "user1", "User One", LocalDate.now(), List.of());

        AccountFullResponseDto mockDto = new AccountFullResponseDto(
                "user1", "Updated User", LocalDate.parse("1990-01-01"), UUID.randomUUID(),
                BigDecimal.valueOf(500), Currency.RUB, 1L);

        AccountShortDto user1Dto = new AccountShortDto("user1", "User One", Currency.RUB);
        AccountShortDto user2Dto = new AccountShortDto("user2", "User Two", Currency.RUB);

        OperationResultDto<Void> errorResponse = OperationResultDto.<Void>builder()
                .success(false).errorCode("INVALID_CURRENCY")
                .message("Допустимые валюты: RUB, USD, CNY").build();
        OperationResultDto<Void> successResponse = OperationResultDto.<Void>builder()
                .success(true).message("Баланс обновлен").build();

        when(userService.getOrCreateUser(any(Jwt.class)))
                .thenReturn(Mono.just(mockDtoUser));
        when(userService.updateProfile(anyString(), any(UserUpdateDto.class)))
                .thenReturn(Mono.just(mockDto));
        when(userService.updatePassword(anyString(), any(PasswordUpdateDto.class)))
                .thenReturn(Mono.just(true));

        when(accountService.getByLogin(anyString(), anyString())).thenReturn(Mono.just(mockDto));
        when(accountService.getOtherAccounts(anyString())).thenReturn(Flux.just(user1Dto, user2Dto));
        when(accountService.changeBalance(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(Mono.just(successResponse));
        when(accountService.openAccount(anyString(), anyString())).thenReturn(Mono.just(errorResponse));
    }
}
