package io.github.habatoo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.habatoo.base.BaseTest;
import io.github.habatoo.dto.UserUpdateDto;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.models.Account;
import io.github.habatoo.models.User;
import io.github.habatoo.repositories.AccountRepository;
import io.github.habatoo.repositories.UserRepository;
import io.github.habatoo.services.AccountService;
import io.github.habatoo.services.OutboxClientService;
import io.github.habatoo.services.OutboxService;
import io.github.habatoo.services.UserService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
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
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Базовый класс для всех сервисных тестов модуля Account.
 * Инкапсулирует настройки контекста и общие зависимости.
 */
@ActiveProfiles("test")
@SpringBootTest(
        classes = AccountApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.liquibase.enabled=false",
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
public abstract class BaseAccountTest extends BaseTest {

    @Autowired
    protected UserService userService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected OutboxService outboxService;

    @Autowired
    protected AccountService accountService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private Counter balanceSuccessCounter;

    @Autowired
    private Counter balanceFailureCounter;

    @Autowired
    private Timer balanceChangeTimer;

    @Autowired
    private Counter userCreatedSuccessCounter;

    @Autowired
    private Counter userCreatedFailureCounter;

    @MockitoBean
    protected OutboxClientService outboxClientService;

    @MockitoBean
    protected ReactiveClientRegistrationRepository reactiveClientRegistrationRepository;

    @MockitoBean
    protected ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository;

    @MockitoBean
    protected ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService;

    @MockitoBean
    protected ReactiveJwtDecoder reactiveJwtDecoder;

    @DynamicPropertySource
    static void specificProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.liquibase.change-log",
                () -> "classpath:db/changelog/account/db.changelog-master.yaml");
    }

    protected Mono<Void> clearDatabase() {
        return accountRepository.deleteAll()
                .then(userRepository.deleteAll());
    }

    protected User createUser(String login) {
        return User.builder()
                .login(login)
                .name("Existing User")
                .birthDate(LocalDate.of(1990, 1, 1))
                .build();
    }

    protected UserUpdateDto createUserUpdateDto(String name) {
        return new UserUpdateDto(
                name,
                LocalDate.of(2000, 1, 1));
    }

    protected Account createAccount(UUID userId, Object balance) {
        return Account.builder()
                .userId(userId)
                .balance(new BigDecimal(balance.toString()))
                .currency(Currency.RUB)
                .build();
    }
}
