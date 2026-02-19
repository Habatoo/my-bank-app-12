package io.github.habatoo.configurations;

import io.github.habatoo.controllers.FallbackController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Тестовый класс для проверки конфигурации безопасности, определенной в {@link SecurityAutoConfiguration}.
 * <p>
 * Данные тесты проверяют корректность настройки цепочки фильтров безопасности (Security Filter Chain),
 * включая правила доступа к защищенным ресурсам и интеграцию с инфраструктурой OAuth2 в реактивной среде.
 * </p>
 * <p>
 * Используется {@link WebFluxTest} для изоляции только веб-слоя и безопасности,
 * без загрузки полного контекста приложения.
 * </p>
 */
@WebFluxTest(
        controllers = FallbackController.class,
        properties = {
                "chassis.outbox.enabled=false",
                "chassis.kafka.enabled=false"
        }
)
@Import(SecurityAutoConfiguration.class)
@DisplayName("Тестирование конфигурации безопасности (SecurityAutoConfiguration)")
class SecurityAutoConfigurationCashedTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    /**
     * Проверяет, что любой запрос к защищенному ресурсу (например, /transfer) без предоставления
     * учетных данных или JWT-токена отклоняется системой безопасности.
     * <p>
     * Ожидается возврат HTTP статуса 401 (Unauthorized) или 302 (Redirect to Login) в зависимости
     * от настройки конкретной точки входа (Entry Point).
     * </p>
     */
    @Test
    @DisplayName("Доступ к API без токена должен возвращать 401 Unauthorized")
    void anyExchangeWithoutTokenShouldReturn401() {
        webTestClient.get()
                .uri("/transfer")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
