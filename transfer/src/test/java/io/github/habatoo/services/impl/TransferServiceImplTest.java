package io.github.habatoo.services.impl;

import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.OperationResultDto;
import io.github.habatoo.dto.TransferDto;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.dto.enums.EventStatus;
import io.github.habatoo.models.Transfer;
import io.github.habatoo.repositories.TransfersRepository;
import io.github.habatoo.services.OutboxClientService;
import io.github.habatoo.services.RateClientService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для сервиса {@link TransferServiceImpl}.
 * Проверяют бизнес-логику переводов, включая сценарии компенсации при сбоях.
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("Тестирование логики переводов (TransferServiceImpl)")
class TransferServiceImplTest {

    private final String SENDER = "sender_user";
    private final String RECIPIENT = "target_user";
    private final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private TransfersRepository transfersRepository;

    @Mock
    private OutboxClientService outboxClientService;

    @Mock
    private CircuitBreakerRegistry registry;

    @Mock
    private RateClientService rateClientService;

    @Mock
    public Counter transferSentFailureCounter;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private TransferServiceImpl transferService;

    private TransferDto transferDto;

    @BeforeEach
    void setUp() {
        transferDto = TransferDto.builder()
                .login(RECIPIENT)
                .value(AMOUNT)
                .fromCurrency(Currency.RUB)
                .toCurrency(Currency.RUB)
                .build();

        CircuitBreaker cb = CircuitBreaker.ofDefaults("transfer-service-cb");
        lenient().when(registry.circuitBreaker("transfer-service-cb")).thenReturn(cb);
        lenient().when(rateClientService.takeRate(any(Currency.class), any(Currency.class))).thenReturn(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Успешный перевод: все этапы проходят корректно")
    void processTransferOperation_Success() {
        OperationResultDto<Void> successResponse = OperationResultDto.<Void>builder()
                .success(true).build();
        mockWebClientResponse(successResponse, successResponse);

        when(transfersRepository.save(any(Transfer.class))).thenReturn(Mono.just(Transfer.builder().build()));
        when(outboxClientService.saveEvent(any(NotificationEvent.class))).thenReturn(Mono.empty());

        Mono<OperationResultDto<TransferDto>> result = transferService.processTransferOperation(SENDER, transferDto);

        StepVerifier.create(result)
                .expectNextMatches(res -> res.isSuccess()
                        && res.getMessage().contains("Перевод выполнен"))
                .verifyComplete();

        verify(transfersRepository, times(1))
                .save(any(Transfer.class));
        verify(outboxClientService, times(1))
                .saveEvent(argThat(event -> event.getStatus() == EventStatus.SUCCESS));
    }

    @Test
    @DisplayName("Ошибка списания: недостаточно средств")
    void processTransferOperation_WithdrawFailure() {
        OperationResultDto<Void> failResponse = OperationResultDto.<Void>builder()
                .success(false)
                .message("Insufficient funds")
                .build();
        mockWebClientResponse(failResponse);

        Mono<OperationResultDto<TransferDto>> result = transferService.processTransferOperation(SENDER, transferDto);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && res.getMessage().contains("Ошибка списания"))
                .verifyComplete();

        verifyNoInteractions(transfersRepository);
        verifyNoInteractions(outboxClientService);
    }

    @Test
    @DisplayName("Ошибка зачисления: запуск компенсации")
    void processTransferOperation_DepositFailure_Compensation() {
        OperationResultDto<Void> success = OperationResultDto.<Void>builder()
                .success(true).build();
        OperationResultDto<Void> fail = OperationResultDto.<Void>builder()
                .success(false).message("Target error").build();

        mockWebClientResponse(success, fail, success);

        when(outboxClientService.saveEvent(any(NotificationEvent.class))).thenReturn(Mono.empty());

        Mono<OperationResultDto<TransferDto>> result = transferService.processTransferOperation(SENDER, transferDto);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && res.getMessage().contains("Средства возвращены"))
                .verifyComplete();

        verify(outboxClientService).saveEvent(
                argThat(event -> event.getStatus() == EventStatus.FAILURE));
        verify(transfersRepository, never()).save(any());
    }

    private void mockWebClientResponse(OperationResultDto<Void>... responses) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        var chain = when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)));
        for (var response : responses) {
            chain = chain.thenReturn(Mono.just(response));
        }
    }
}
