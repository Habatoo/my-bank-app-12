package io.github.habatoo.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Тестирование контроллера резервных ответов {@link FallbackController}.
 * <p>
 * Проверяет, что эндпоинты возвращают корректные HTTP-статусы и текстовые сообщения
 * для информирования пользователя о временной недоступности микросервисов.
 * </p>
 */
@WebFluxTest(controllers = FallbackController.class,
        properties = {
                "chassis.outbox.enabled=false",
                "chassis.kafka.enabled=false"
        })
@DisplayName("Юнит-тесты контроллера FallbackController")
class FallbackControllerCashedTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ErrorWebExceptionHandler errorWebExceptionHandler;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ReactiveOAuth2AuthorizedClientService authorizedClientService;

    /**
     * Тест проверяет fallback для Cash Service.
     */
    @Test
    @DisplayName("GET /fallback/cash-unavailable - Возврат статуса 503")
    void cashFallbackTest() {
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/fallback/cash-unavailable")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .isEqualTo("Сервис кассовых операций временно недоступен. Попробуйте позже.");
    }

    /**
     * Тест проверяет fallback для Account Service.
     */
    @Test
    @DisplayName("GET /fallback/account-unavailable - Возврат статуса 503")
    void accountFallbackTest() {
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/fallback/account-unavailable")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .isEqualTo("Сервис по работе со счетом временно недоступен. Попробуйте позже.");
    }

    /**
     * Тест проверяет fallback для Transfer Service.
     */
    @Test
    @DisplayName("GET /fallback/transfer-unavailable - Возврат статуса 503")
    void transferFallbackTest() {
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/fallback/transfer-unavailable")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .isEqualTo("Сервис переводов временно недоступен. Попробуйте позже.");
    }
}
