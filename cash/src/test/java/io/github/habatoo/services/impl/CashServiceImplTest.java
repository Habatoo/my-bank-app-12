package io.github.habatoo.services.impl;

import io.github.habatoo.dto.CashDto;
import io.github.habatoo.dto.OperationResultDto;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.dto.enums.OperationType;
import io.github.habatoo.models.Cash;
import io.github.habatoo.repositories.OperationsRepository;
import io.github.habatoo.services.OutboxClientService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Тестирование реализации сервиса кассовых операций {@link CashServiceImpl}.
 * Проверяет бизнес-логику проведения платежей, сохранение в историю,
 * работу с Outbox и механизм компенсации при ошибках БД.
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("Юнит-тесты сервиса CashServiceImpl")
class CashServiceImplTest {

    private final String LOGIN = "test_user";
    private final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock
    private OperationsRepository operationsRepository;
    @Mock
    private OutboxClientService outboxClientService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private WebClient webClient;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private Jwt jwt;
    @Mock
    private Counter cashSentFailureCounter;

    @InjectMocks
    private CashServiceImpl cashService;

    @BeforeEach
    void setUp() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("cashServiceCB");
        lenient().when(circuitBreakerRegistry.circuitBreaker("cashServiceCB")).thenReturn(cb);
        lenient().when(jwt.getClaimAsString("preferred_username")).thenReturn(LOGIN);
        lenient().when(jwt.getSubject()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("Успешное пополнение (PUT): парсинг строк и вызов внешнего сервиса")
    void processCashOperationPutSuccessTest() {
        OperationResultDto<Void> accountResponse = OperationResultDto.<Void>builder()
                .success(true)
                .build();

        mockWebClientPost(accountResponse);
        when(operationsRepository.save(any(Cash.class))).thenReturn(Mono.just(new Cash()));
        when(outboxClientService.saveEvent(any())).thenReturn(Mono.empty());

        Mono<OperationResultDto<CashDto>> result = cashService.processCashOperation(
                BigDecimal.valueOf(1000), "put", "rub", jwt);

        StepVerifier.create(result)
                .expectNextMatches(res -> res.isSuccess()
                        && res.getData().getCurrency() == Currency.RUB
                        && res.getData().getAction() == OperationType.PUT)
                .verifyComplete();

        verify(operationsRepository).save(any());
    }

    @Test
    @DisplayName("Ошибка валидации: неверный формат валюты")
    void processCashOperationInvalidCurrencyTest() {
        Mono<OperationResultDto<CashDto>> result = cashService.processCashOperation(
                BigDecimal.valueOf(1000), "PUT", "INVALID_CUR", jwt);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && res.getMessage().contains("Неподдерживаемая валюта"))
                .verifyComplete();

        verifyNoInteractions(webClient);
        verifyNoInteractions(operationsRepository);
    }

    @Test
    @DisplayName("Ошибка валидации: отрицательная сумма")
    void processCashOperationNegativeValueTest() {
        Mono<OperationResultDto<CashDto>> result = cashService.processCashOperation(
                BigDecimal.valueOf(-100), "PUT", "RUB", jwt);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && res.getMessage().contains("должна быть положительной"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Компенсация: ошибка БД после списания")
    void compensationLogicTest() {
        OperationResultDto<Void> accountSuccess = OperationResultDto.<Void>builder().success(true).build();
        mockWebClientPost(accountSuccess);

        when(operationsRepository.save(any(Cash.class)))
                .thenReturn(Mono.error(new RuntimeException("DB Failure")));
        when(outboxClientService.saveEvent(any())).thenReturn(Mono.empty());

        Mono<OperationResultDto<CashDto>> result = cashService.processCashOperation(
                BigDecimal.valueOf(500), "GET", "USD", jwt);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && res.getMessage().contains("Сбой сохранения"))
                .verifyComplete();

        verify(webClient.post(), times(2)).uri(any(Function.class));
        verify(outboxClientService).saveEvent(argThat(event -> event.getStatus().name().equals("FAILURE")));
    }

    @SuppressWarnings("unchecked")
    private void mockWebClientPost(OperationResultDto<Void> response) {
        when(webClient.post()
                .uri(any(Function.class))
                .retrieve()
                .bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(response));
    }
}
