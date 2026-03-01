package io.github.habatoo.services.impl;

import io.github.habatoo.dto.AccountFullResponseDto;
import io.github.habatoo.dto.AccountShortDto;
import io.github.habatoo.dto.NotificationEvent;
import io.github.habatoo.dto.OperationResultDto;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.dto.enums.EventStatus;
import io.github.habatoo.dto.enums.EventType;
import io.github.habatoo.models.Account;
import io.github.habatoo.models.User;
import io.github.habatoo.repositories.AccountRepository;
import io.github.habatoo.repositories.UserRepository;
import io.github.habatoo.services.AccountService;
import io.github.habatoo.services.OutboxClientService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * {@inheritDoc}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final OutboxClientService outboxClientService;

    private final Counter balanceSuccessCounter;
    private final Counter balanceFailureCounter;
    private final Counter accountOpenCounter;
    private final Timer balanceChangeTimer;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<AccountFullResponseDto> getByLogin(String login, String currencyStr) {
        return parseCurrency(currencyStr)
                .flatMap(currency -> findUserByLogin(login)
                        .flatMap(user -> accountRepository.findByUserIdAndCurrency(user.getId(), currency)
                                .map(acc -> mapToFullResponse(user, acc))
                        )
                )
                .switchIfEmpty(Mono.error(new NoSuchElementException("Счет не найден для указанной валюты")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<AccountShortDto> getOtherAccounts(String currentLogin) {
        return userRepository.findAllByLoginNot(currentLogin)
                .flatMap(user -> accountRepository.findAllByUserId(user.getId())
                        .map(acc -> AccountShortDto.builder()
                                .login(user.getLogin())
                                .name(user.getName())
                                .currency(acc.getCurrency())
                                .build())
                );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Mono<OperationResultDto<Void>> changeBalance(String login, BigDecimal delta, String currencyStr) {
        Timer.Sample sample = Timer.start();

        return parseCurrency(currencyStr)
                .flatMap(currency -> findUserByLogin(login)
                        .flatMap(user -> accountRepository.findByUserIdAndCurrency(
                                user.getId(), currency)))
                .flatMap(account -> getAccount(login, delta, account))
                .doOnNext(result -> {
                    if (result.isSuccess()) {
                        balanceSuccessCounter.increment();
                    } else {
                        balanceFailureCounter.increment();
                    }
                })
                .doOnError(e -> balanceFailureCounter.increment())
                .doFinally(signal ->
                        sample.stop(balanceChangeTimer)
                )
                .switchIfEmpty(Mono.just(
                        createErrorResponse("ACCOUNT_NOT_FOUND", "Счет не найден")))
                .onErrorResume(e -> Mono.just(
                        createErrorResponse("VALIDATION_ERROR", e.getMessage())));
    }

    @Override
    @Transactional
    public Mono<OperationResultDto<Void>> openAccount(String login, String currencyStr) {

        return parseCurrency(currencyStr)
                .flatMap(currency -> findUserByLogin(login)
                        .flatMap(user -> accountRepository.findByUserIdAndCurrency(user.getId(), currency)
                                .flatMap(exists -> {
                                    accountOpenCounter.increment();
                                    return Mono.just(
                                            createErrorResponse("ACCOUNT_EXISTS", "Счет уже открыт"));
                                })
                                .switchIfEmpty(createNewAccount(user, currency)
                                        .doOnSuccess(r -> accountOpenCounter.increment())
                                )))
                .doOnError(e -> accountOpenCounter.increment())
                .onErrorResume(e -> Mono.just(createErrorResponse("ERROR", e.getMessage())));
    }

    private @NotNull Mono<OperationResultDto<Void>> getAccount(
            String login,
            BigDecimal delta,
            Account account) {
        BigDecimal newBalance = account.getBalance().add(delta);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return Mono.just(createErrorResponse("INSUFFICIENT_FUNDS", "Недостаточно средств"));
        }
        return updateAccountBalance(account, login, delta, newBalance);
    }

    private Mono<Currency> parseCurrency(String curStr) {
        return Mono.fromCallable(() -> Currency.valueOf(curStr.toUpperCase()))
                .onErrorMap(e -> new IllegalArgumentException("Неподдерживаемая валюта: " + curStr));
    }

    private Mono<User> findUserByLogin(String login) {
        return userRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Пользователь не найден: " + login)));
    }

    private Mono<OperationResultDto<Void>> createNewAccount(User user, Currency currency) {
        Account account = Account.builder()
                .userId(user.getId())
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .build();

        return accountRepository.save(account)
                .then(outboxClientService.saveEvent(getCreateAccountNotificationEvent(user, currency)))
                .thenReturn(buildSuccessResponse("Счет в " + currency + " открыт"));
    }

    private Mono<OperationResultDto<Void>> updateAccountBalance(Account acc, String login, BigDecimal delta, BigDecimal newBalance) {
        acc.setBalance(newBalance);
        acc.setUpdatedAt(LocalDateTime.now());

        return accountRepository.save(acc)
                .then(outboxClientService.saveEvent(getNotificationEvent(login, delta, newBalance)))
                .thenReturn(buildSuccessResponse("Баланс обновлен"));
    }

    private AccountFullResponseDto mapToFullResponse(User user, Account acc) {
        return AccountFullResponseDto.builder()
                .login(user.getLogin()).name(user.getName())
                .birthDate(user.getBirthDate()).balance(acc.getBalance())
                .currency(acc.getCurrency()).build();
    }

    private OperationResultDto<Void> buildSuccessResponse(String msg) {
        return OperationResultDto.<Void>builder().success(true).message(msg).build();
    }

    private OperationResultDto<Void> createErrorResponse(String code, String msg) {
        return OperationResultDto.<Void>builder().success(false).errorCode(code).message(msg).build();
    }

    private NotificationEvent getCreateAccountNotificationEvent(User user, Currency cur) {
        return NotificationEvent.builder()
                .username(user.getLogin()).eventType(EventType.CREATE_ACCOUNT)
                .status(EventStatus.SUCCESS).message("Создан счет: " + cur)
                .payload(Map.of("currency", cur.name())).build();
    }

    private NotificationEvent getNotificationEvent(String login, BigDecimal delta, BigDecimal balance) {
        return NotificationEvent.builder()
                .username(login).eventType(delta.signum() > 0 ? EventType.DEPOSIT : EventType.WITHDRAW)
                .status(EventStatus.SUCCESS).message("Изменение баланса на " + delta)
                .payload(Map.of("delta", delta, "balance", balance)).build();
    }
}
