package io.github.habatoo.cofigurations;

import io.github.habatoo.controllers.MainController;
import io.github.habatoo.controllers.OperationsController;
import io.github.habatoo.services.CashFrontService;
import io.github.habatoo.services.FrontService;
import io.github.habatoo.services.TransferFrontService;
import io.github.habatoo.services.UserFrontService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Тесты для проверки конфигурации безопасности приложения.
 * <p>
 * Проверяют корректность настройки прав доступа (permitAll/authenticated)
 * и работу цепочки фильтров безопасности WebFlux.
 * </p>
 */
@WebFluxTest(
        controllers = {MainController.class, OperationsController.class},
        properties = {
                "chassis.outbox.enabled=false",
                "chassis.kafka.enabled=false"
        }
)
@Import(SecurityAutoConfiguration.class)
@DisplayName("Юнит-тесты конфигурации безопасности (SecurityAutoConfiguration)")
class SecurityAutoConfigurationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private FrontService frontService;

    @MockitoBean
    private CashFrontService cashFrontService;

    @MockitoBean
    private TransferFrontService transferFrontService;

    @MockitoBean
    private UserFrontService userFrontService;

    @BeforeEach
    void setUp() {
        when(frontService.showMainPage(any(), any()))
                .thenReturn(Mono.empty());
    }

    /**
     * Проверяет, что доступ к защищенным ресурсам (например, /main)
     * перенаправляет неавторизованного пользователя на страницу входа.
     */
    @Test
    @DisplayName("Запрет доступа к защищенным ресурсам без авторизации (редирект на Login)")
    void shouldRedirectToLoginWhenUnauthenticatedTest() {
        webTestClient.get().uri("/main")
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    /**
     * Проверяет доступ к защищенным ресурсам при наличии активной сессии.
     * <p>
     * Используется аннотация {@link WithMockUser} для имитации аутентифицированного пользователя.
     * </p>
     */
    @Test
    @WithMockUser
    @DisplayName("Доступ к главной странице для авторизованного пользователя")
    void shouldAllowAccessToMainWhenAuthenticatedTest() {
        webTestClient.get().uri("/main")
                .exchange()
                .expectStatus().isOk();
    }
}
