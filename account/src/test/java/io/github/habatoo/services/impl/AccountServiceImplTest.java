package io.github.habatoo.services.impl;

import io.github.habatoo.dto.AccountFullResponseDto;
import io.github.habatoo.dto.AccountShortDto;
import io.github.habatoo.dto.OperationResultDto;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.models.Account;
import io.github.habatoo.models.User;
import io.github.habatoo.repositories.AccountRepository;
import io.github.habatoo.repositories.UserRepository;
import io.github.habatoo.services.OutboxClientService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тестирование реализации сервиса аккаунтов {@link AccountServiceImpl}.
 * Проверяет бизнес-логику работы с балансом, фильтрацию пользователей и агрегацию данных.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Юнит-тесты сервиса AccountServiceImpl")
class AccountServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OutboxClientService outboxClientService;

    @Mock
    private Counter balanceSuccessCounter;

    @Mock
    private Counter balanceFailureCounter;

    @Mock
    private Counter accountOpenCounter;

    @Mock
    private Timer balanceChangeTimer;

    @InjectMocks
    private AccountServiceImpl accountService;

    /**
     * Тест успешного получения полной информации об аккаунте.
     */
    @Test
    @DisplayName("Получение данных по логину: успех")
    void getByLoginSuccessTest() {
        String login = "test_user";
        String currency = "RUB";
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).login(login).name("Ivan").build();
        Account account = Account.builder().userId(userId).balance(BigDecimal.valueOf(100)).build();

        when(userRepository.findByLogin(login)).thenReturn(Mono.just(user));
        when(accountRepository.findByUserIdAndCurrency(userId, Currency.RUB)).thenReturn(Mono.just(account));

        Mono<AccountFullResponseDto> result = accountService.getByLogin(login, currency);

        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.getLogin().equals(login)
                        && dto.getBalance().intValue() == 100)
                .verifyComplete();
    }

    /**
     * Тест получения списка других пользователей (исключая текущего).
     */
    @Test
    @DisplayName("Получение списка других аккаунтов: успех")
    void getOtherAccountsTest() {
        String currentLogin = "me";
        java.util.UUID otherUserId = java.util.UUID.randomUUID();

        User other = User.builder()
                .id(otherUserId)
                .login("other")
                .name("Other User")
                .build();

        Account otherAccount = Account.builder()
                .userId(otherUserId)
                .currency(Currency.RUB)
                .balance(BigDecimal.TEN)
                .build();

        when(userRepository.findAllByLoginNot(currentLogin)).thenReturn(Flux.just(other));
        when(accountRepository.findAllByUserId(otherUserId)).thenReturn(Flux.just(otherAccount));

        Flux<AccountShortDto> result = accountService.getOtherAccounts(currentLogin);

        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.getLogin().equals("other") &&
                                dto.getName().equals("Other User") &&
                                dto.getCurrency() == Currency.RUB)
                .verifyComplete();
    }

    /**
     * Тест успешного пополнения баланса.
     */
    @Test
    @DisplayName("Изменение баланса: успешное пополнение")
    void changeBalanceDepositSuccessTest() {
        String login = "user";
        String currency = "RUB";
        UUID userId = UUID.randomUUID();
        BigDecimal delta = BigDecimal.valueOf(50);
        User user = User.builder().id(userId).login(login).build();
        Account account = Account.builder().userId(userId).balance(BigDecimal.valueOf(100)).build();

        when(userRepository.findByLogin(login)).thenReturn(Mono.just(user));
        when(accountRepository.findByUserIdAndCurrency(userId, Currency.RUB)).thenReturn(Mono.just(account));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(account));
        when(outboxClientService.saveEvent(any())).thenReturn(Mono.empty());

        Mono<OperationResultDto<Void>> result = accountService.changeBalance(login, delta, currency);

        StepVerifier.create(result)
                .expectNextMatches(OperationResultDto::isSuccess)
                .verifyComplete();

        verify(accountRepository).save(argThat(acc -> acc.getBalance().equals(BigDecimal.valueOf(150))));
        verify(outboxClientService).saveEvent(any());
    }

    /**
     * Тест отклонения операции при попытке списать больше, чем есть на счету.
     */
    @Test
    @DisplayName("Изменение баланса: ошибка при недостаточном балансе")
    void changeBalanceInsufficientFundsTest() {
        String login = "user";
        String currency = "RUB";
        UUID userId = UUID.randomUUID();
        BigDecimal withdrawDelta = BigDecimal.valueOf(-200);
        User user = User.builder().id(userId).login(login).build();
        Account account = Account.builder().userId(userId).balance(BigDecimal.valueOf(100)).build();

        when(userRepository.findByLogin(login)).thenReturn(Mono.just(user));
        when(accountRepository.findByUserIdAndCurrency(userId, Currency.RUB)).thenReturn(Mono.just(account));

        Mono<OperationResultDto<Void>> result = accountService.changeBalance(login, withdrawDelta, currency);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && "INSUFFICIENT_FUNDS".equals(res.getErrorCode()))
                .verifyComplete();

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Открытие счета: успех")
    void openAccountSuccessTest() {
        String login = "test_user";
        String currencyStr = "USD";
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).login(login).build();

        when(userRepository.findByLogin(login)).thenReturn(Mono.just(user));
        when(accountRepository.findByUserIdAndCurrency(userId, Currency.USD)).thenReturn(Mono.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outboxClientService.saveEvent(any())).thenReturn(Mono.empty());

        Mono<OperationResultDto<Void>> result = accountService.openAccount(login, currencyStr);

        StepVerifier.create(result)
                .expectNextMatches(res -> res.isSuccess()
                        && res.getMessage().equals("Счет в USD открыт"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Изменение баланса: ошибка, если пользователь не найден")
    void changeBalanceUserNotFoundTest() {
        String login = "unknown";
        String currency = "RUB";
        when(userRepository.findByLogin(login)).thenReturn(Mono.empty());

        Mono<OperationResultDto<Void>> result = accountService.changeBalance(login, BigDecimal.ONE, currency);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && "VALIDATION_ERROR".equals(res.getErrorCode())
                        && res.getMessage().contains("Пользователь не найден"))
                .verifyComplete();
    }

    /**
     * Проверка ошибки при попытке открыть счет в валюте, которой нет в Enum Currency.
     */
    @Test
    @DisplayName("Открытие счета: ошибка при неверном формате валюты")
    void openAccountInvalidFormatTest() {
        String login = "user";
        String invalidCurrency = "INVALID_VAL";

        Mono<OperationResultDto<Void>> result = accountService.openAccount(login, invalidCurrency);

        StepVerifier.create(result)
                .expectNextMatches(dto -> !dto.isSuccess()
                        && dto.getMessage().contains("Неподдерживаемая валюта"))
                .verifyComplete();
    }

    /**
     * Проверка ошибки, если указанный логин пользователя не существует в базе данных.
     */
    @Test
    @DisplayName("Открытие счета: ошибка, если пользователь не найден")
    void openAccountUserNotFoundTest() {
        String login = "missing_user";
        String currencyStr = "RUB";

        when(userRepository.findByLogin(login)).thenReturn(Mono.empty());

        Mono<OperationResultDto<Void>> result = accountService.openAccount(login, currencyStr);

        StepVerifier.create(result)
                .expectNextMatches(dto -> !dto.isSuccess()
                        && dto.getMessage().contains("Пользователь не найден"))
                .verifyComplete();
    }

    /**
     * Проверка бизнес-ограничения: нельзя открыть второй счет в той же валюте для одного пользователя.
     */
    /**
     * Проверка бизнес-ограничения: нельзя открыть второй счет в той же валюте для одного пользователя.
     */
    @Test
    @DisplayName("Открытие счета: ошибка, если счет в этой валюте уже существует")
    void openAccountAlreadyExistsTest() {
        String login = "user";
        String currencyStr = "CNY";
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).login(login).build();

        Account existingAccount = Account.builder()
                .userId(userId)
                .currency(Currency.CNY)
                .balance(BigDecimal.ZERO)
                .build();

        when(userRepository.findByLogin(login)).thenReturn(Mono.just(user));
        when(accountRepository.findByUserIdAndCurrency(userId, Currency.CNY)).thenReturn(Mono.just(existingAccount));
        lenient().when(accountRepository.save(any(Account.class))).thenReturn(Mono.empty());
        lenient().when(outboxClientService.saveEvent(any())).thenReturn(Mono.empty());

        Mono<OperationResultDto<Void>> result = accountService.openAccount(login, currencyStr);

        StepVerifier.create(result)
                .expectNextMatches(res -> !res.isSuccess()
                        && "ACCOUNT_EXISTS".equals(res.getErrorCode()))
                .verifyComplete();
    }

    /**
     * Проверка обработки ситуации, когда передана пустая строка валюты.
     */
    @Test
    @DisplayName("Открытие счета: ошибка при пустой строке валюты")
    void openAccountEmptyCurrencyTest() {
        String login = "user";
        String currencyStr = "";

        Mono<OperationResultDto<Void>> result = accountService.openAccount(login, currencyStr);

        StepVerifier.create(result)
                .expectNextMatches(dto -> !dto.isSuccess() &&
                        dto.getMessage().equals("Неподдерживаемая валюта: "))
                .verifyComplete();
    }
}
