package io.github.habatoo.controllers;

import io.github.habatoo.FrontApplication;
import io.github.habatoo.configurations.TestSecurityConfiguration;
import io.github.habatoo.dto.CashDto;
import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.TransferDto;
import io.github.habatoo.repositories.OutboxRepository;
import io.github.habatoo.services.CashFrontService;
import io.github.habatoo.services.FrontService;
import io.github.habatoo.services.TransferFrontService;
import io.github.habatoo.services.UserFrontService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.result.view.RedirectView;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {
                FrontApplication.class,
                TestSecurityConfiguration.class
        },
        properties = {
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.liquibase.enabled=false",
                "spring.security.enabled=false",
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
    protected FrontService frontService;

    @MockitoBean
    protected OutboxRepository outboxRepository;

    @MockitoBean
    protected CashFrontService cashFrontService;

    @MockitoBean
    protected TransferFrontService transferFrontService;

    @MockitoBean
    protected UserFrontService userFrontService;

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private ReactiveClientRegistrationRepository reactiveClientRegistrationRepository;

    @MockitoBean
    private KafkaSender<String, NotificationEvent> kafkaSender;

    @MockitoBean
    private ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService;

    @MockitoBean
    private ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository;

    @Autowired
    protected WebTestClient webTestClient;

    @BeforeEach
    void setup() {
        setupServiceMocks();
        RestAssuredWebTestClient.webTestClient(webTestClient);
    }

    private void setupServiceMocks() {
        when(cashFrontService.moveMoney(any(CashDto.class)))
                .thenReturn(Mono.just("redirect:/main?info=success"));
        when(transferFrontService.sendMoney(any(TransferDto.class)))
                .thenReturn(Mono.just("redirect:/main?info=transfer_ok"));
        when(userFrontService.updateProfile(any(ServerWebExchange.class)))
                .thenReturn(Mono.just(new RedirectView("/main")));
        when(frontService.showMainPage(any(), any()))
                .thenReturn(Mono.just(Rendering.view("main")
                        .modelAttribute("info", "Операция выполнена")
                        .modelAttribute("error", "Произошла ошибка")
                        .build()));

        webTestClient = WebTestClient
                .bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build()
                .mutateWith(mockJwt()
                        .jwt(builder -> builder
                                .subject("367c3cf3-af3e-44af-ab7b-6d2034c6fca6")
                                .claim("preferred_username", "user1")
                        ));
    }
}
