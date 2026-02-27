package io.github.habatoo.services.impl;

import io.github.habatoo.dto.CashDto;
import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.OperationResultDto;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.dto.enums.EventStatus;
import io.github.habatoo.dto.enums.EventType;
import io.github.habatoo.dto.enums.OperationType;
import io.github.habatoo.models.Cash;
import io.github.habatoo.repositories.OperationsRepository;
import io.github.habatoo.services.CashService;
import io.github.habatoo.services.OutboxClientService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * {@inheritDoc}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashServiceImpl implements CashService {

    private final WebClient webClient;
    private final OperationsRepository operationsRepository;
    private final OutboxClientService outboxClientService;
    private final CircuitBreakerRegistry registry;
    private final Counter cashSentFailureCounter;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<OperationResultDto<CashDto>> processCashOperation(
            BigDecimal value,
            String action,
            String currency,
            Jwt jwt) {

        String login = jwt.getClaimAsString("preferred_username");
        String userId = jwt.getSubject();

        return Mono.zip(parseOperationType(action), parseCurrency(currency))
                .flatMap(tuple -> {
                    OperationType opType = tuple.getT1();
                    Currency cur = tuple.getT2();

                    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.error(new IllegalArgumentException("Сумма должна быть положительной"));
                    }

                    CashDto dto = CashDto.builder()
                            .userId(UUID.fromString(userId))
                            .value(value)
                            .action(opType)
                            .currency(cur)
                            .createdAt(LocalDateTime.now())
                            .build();

                    BigDecimal delta = (opType == OperationType.PUT) ? value : value.negate();
                    return executeTransaction(login, dto, delta);
                })
                .onErrorResume(e -> {
                    cashSentFailureCounter.increment();
                    log.error("Ошибка валидации параметров для {}: {}", login, e.getMessage());
                    return Mono.just(errorResponse(e.getMessage()));
                });
    }

    private Mono<Currency> parseCurrency(String curStr) {
        return Mono.fromCallable(() -> Currency.valueOf(curStr.toUpperCase()))
                .onErrorMap(e -> new IllegalArgumentException("Неподдерживаемая валюта: " + curStr));
    }

    private Mono<OperationType> parseOperationType(String oprStr) {
        return Mono.fromCallable(() -> OperationType.valueOf(oprStr.toUpperCase()))
                .onErrorMap(e -> new IllegalArgumentException("Неподдерживаемый тип операции: " + oprStr));
    }

    private Mono<OperationResultDto<CashDto>> executeTransaction(String login, CashDto dto, BigDecimal delta) {
        return callAccountService(login, delta, dto.getCurrency().name())
                .flatMap(res -> res.isSuccess()
                        ? saveAndNotify(login, dto, delta)
                        : Mono.just(errorResponse(res.getMessage())));
    }

    private Mono<OperationResultDto<CashDto>> saveAndNotify(String login, CashDto dto, BigDecimal delta) {
        return operationsRepository.save(mapToEntity(login, dto))
                .then(sendNotification(login, dto, EventStatus.SUCCESS))
                .thenReturn(successResponse(dto))
                .onErrorResume(e -> {
                    log.error("DB Error. Starting compensation for {}: {}", login, e.getMessage());
                    return callAccountService(login, delta.negate(), dto.getCurrency().name())
                            .then(sendNotification(login, dto, EventStatus.FAILURE))
                            .thenReturn(errorResponse("Сбой сохранения в БД. Средства возвращены на счет."));
                });
    }

    private Mono<OperationResultDto<Void>> callAccountService(String login, BigDecimal amt, String cur) {
        CircuitBreaker cb = registry.circuitBreaker("cashServiceCB");
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/account/balance")
                        .queryParam("login", login)
                        .queryParam("amount", amt)
                        .queryParam("currency", cur)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<OperationResultDto<Void>>() {})
                .transformDeferred(CircuitBreakerOperator.of(cb));
    }

    private Mono<Void> sendNotification(String login, CashDto dto, EventStatus status) {
        String actionName = dto.getAction() == OperationType.PUT ? "Внесение" : "Снятие";
        String msg = (status == EventStatus.SUCCESS)
                ? String.format("%s %.2f %s выполнено успешно", actionName, dto.getValue(), dto.getCurrency())
                : "Ошибка сохранения транзакции. Баланс восстановлен.";

        return outboxClientService.saveEvent(NotificationEvent.builder()
                .username(login).status(status).message(msg).sourceService("cash-service")
                .eventType(dto.getAction() == OperationType.PUT ? EventType.DEPOSIT : EventType.WITHDRAW).build());
    }

    private Cash mapToEntity(String login, CashDto dto) {
        return Cash.builder()
                .username(login)
                .amount(dto.getValue())
                .currency(dto.getCurrency())
                .operationType(dto.getAction())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private OperationResultDto<CashDto> successResponse(CashDto dto) {
        return OperationResultDto.<CashDto>builder().success(true).data(dto).message("Успешно").build();
    }

    private OperationResultDto<CashDto> errorResponse(String msg) {
        return OperationResultDto.<CashDto>builder().success(false).message(msg).build();
    }
}
