package io.github.habatoo.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Интеграционные тесты для контроллера FallbackController.
 */
@WebFluxTest(
        controllers = FallbackController.class,
        properties = {
                "chassis.outbox.enabled=false",
                "chassis.kafka.enabled=false"
        }
)
@ActiveProfiles("test")
class FallbackControllerIntegrationTest {

    private static final String mockUsername = "test-user";

    @MockitoBean
    private ReactiveClientRegistrationRepository reactiveClientRegistrationRepository;

    @MockitoBean
    private ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService;

    @MockitoBean
    private ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository;

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Проверка fallback для недоступного сервиса кассовых операций.
     * Ожидается статус 503 и соответствующее сообщение.
     */
    @Test
    @DisplayName("Проверка fallback для недоступного сервиса с имитацией JW и ROLE_USER")
    void cashFallbackTest() {
        webTestClient.mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(jwt -> jwt.claim("preferred_username", mockUsername)))
                .get()
                .uri("/fallback/cash-unavailable")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .isEqualTo("Сервис кассовых операций временно недоступен. Попробуйте позже.");
    }

    /**
     * Проверка fallback для недоступного сервиса по работе со счетом.
     * Ожидается статус 503 и соответствующее сообщение.
     */
    @Test
    @DisplayName("Проверка fallback для недоступного сервиса с имитацией JWT")
    void accountFallbackTest() {
        webTestClient.mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(jwt -> jwt.claim("preferred_username", mockUsername)))
                .get()
                .uri("/fallback/account-unavailable")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .isEqualTo("Сервис по работе со счетом временно недоступен. Попробуйте позже.");
    }

    /**
     * Проверка fallback для недоступного сервиса переводов.
     * Ожидается статус 503 и соответствующее сообщение.
     */
    @Test
    @DisplayName("Проверка fallback для переводов с имитацией JWT")
    void transferFallbackTest() {
        webTestClient.mutateWith(mockJwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(jwt -> jwt.claim("preferred_username", mockUsername)))
                .get()
                .uri("/fallback/transfer-unavailable")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .isEqualTo("Сервис переводов временно недоступен. Попробуйте позже.");
    }
}
