package io.github.habatoo.services.impl;

import io.github.habatoo.dto.*;
import io.github.habatoo.dto.enums.Currency;
import io.github.habatoo.dto.enums.EventStatus;
import io.github.habatoo.dto.enums.EventType;
import io.github.habatoo.models.Account;
import io.github.habatoo.models.User;
import io.github.habatoo.repositories.AccountRepository;
import io.github.habatoo.repositories.UserRepository;
import io.github.habatoo.services.OutboxClientService;
import io.github.habatoo.services.UserService;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * {@inheritDoc}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final OutboxClientService outboxClientService;
    private final WebClient backgroundWebClient;

    private final Counter userCreatedSuccessCounter;
    private final Counter userCreatedFailureCounter;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String keycloakIssuerUri;

    @Override
    @Transactional
    public Mono<UserProfileResponseDto> getOrCreateUser(Jwt jwt) {
        String login = jwt.getClaimAsString("preferred_username");

        return userRepository.findByLogin(login)
                .flatMap(this::enrichWithAccounts)
                .switchIfEmpty(Mono.defer(() -> registerUser(jwt)))
                .doOnError(e -> {
                    userCreatedFailureCounter.increment();
                    log.error("Ошибка при создании пользователя {}", login, e);
                });
    }

    @Override
    @Transactional
    public Mono<AccountFullResponseDto> updateProfile(String login, UserUpdateDto dto) {
        return userRepository.findByLogin(login)
                .flatMap(user -> {
                    user.setName(dto.getName());
                    user.setBirthDate(dto.getBirthDate());
                    return userRepository.save(user);
                })
                .flatMap(user -> accountRepository.findAllByUserId(user.getId())
                        .filter(acc -> acc.getCurrency() == Currency.RUB)
                        .next()
                        .map(acc -> mapToFullDto(user, acc))
                        .switchIfEmpty(Mono.just(mapToFullDto(user, null))))
                .flatMap(response -> saveUpdateNotification(login, dto).thenReturn(response));
    }

    @Override
    public Mono<Boolean> updatePassword(String login, PasswordUpdateDto dto) {
        String adminUrl = keycloakIssuerUri.replace("/realms/", "/admin/realms/");

        return findKeycloakUserId(adminUrl, login)
                .flatMap(userId -> resetKeycloakPassword(adminUrl, userId, dto.getPassword()))
                .thenReturn(true);
    }

    private Mono<Void> resetKeycloakPassword(String adminUrl, String userId, String password) {
        return backgroundWebClient.put()
                .uri(adminUrl + "/users/{id}/reset-password", userId)
                .bodyValue(Map.of("type", "password", "value", password, "temporary", false))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private Mono<String> findKeycloakUserId(String adminUrl, String login) {
        return backgroundWebClient.get()
                .uri(adminUrl + "/users?username={login}", login)
                .retrieve()
                .bodyToFlux(Map.class)
                .filter(u -> login.equals(u.get("username")))
                .next()
                .map(u -> (String) u.get("id"))
                .switchIfEmpty(Mono.error(new NoSuchElementException("Keycloak user not found")));
    }

    private Mono<UserProfileResponseDto> enrichWithAccounts(User user) {
        return accountRepository.findAllByUserId(user.getId())
                .map(acc -> AccountDto.builder()
                        .id(acc.getId())
                        .balance(acc.getBalance())
                        .currency(acc.getCurrency())
                        .build())
                .collectList()
                .map(accs -> UserProfileResponseDto.builder()
                        .login(user.getLogin()).name(user.getName())
                        .birthDate(user.getBirthDate()).accounts(accs).build());
    }

    private Mono<UserProfileResponseDto> registerUser(Jwt jwt) {
        User user = buildUserFromToken(jwt);
        return userRepository.save(user)
                .flatMap(saved -> outboxClientService.saveEvent(createRegEvent(saved))
                        .thenReturn(UserProfileResponseDto.builder()
                                .login(saved.getLogin())
                                .name(saved.getName())
                                .birthDate(saved.getBirthDate())
                                .accounts(List.of())
                                .build()))
                .doOnSuccess(resp -> {
                    userCreatedSuccessCounter.increment();
                    log.info("Пользователь {} успешно создан", user.getLogin());
                })
                .doOnError(e -> {
                    userCreatedFailureCounter.increment();
                    log.error("Ошибка регистрации пользователя {}", user.getLogin(), e);
                });
    }

    private User buildUserFromToken(Jwt jwt) {
        return User.builder()
                .login(jwt.getClaimAsString("preferred_username"))
                .name(jwt.getClaimAsString("given_name") != null ? jwt.getClaimAsString("given_name") : "New User")
                .birthDate(parseBirthDate(jwt))
                .build();
    }

    private LocalDate parseBirthDate(Jwt jwt) {
        String bd = jwt.getClaimAsString("birthdate");
        return bd != null ? LocalDate.parse(bd) : LocalDate.now();
    }

    private Mono<Void> saveUpdateNotification(String login, UserUpdateDto dto) {
        return outboxClientService.saveEvent(NotificationEvent.builder()
                .username(login).eventType(EventType.UPDATE_PROFILE).status(EventStatus.SUCCESS)
                .message("Данные профиля обновлены").build());
    }

    private NotificationEvent createRegEvent(User user) {
        return NotificationEvent.builder()
                .username(user.getLogin()).eventType(EventType.REGISTRATION).status(EventStatus.SUCCESS)
                .message("Добро пожаловать в Chassis Bank!").build();
    }

    private AccountFullResponseDto mapToFullDto(User user, Account acc) {
        return AccountFullResponseDto.builder()
                .login(user.getLogin()).name(user.getName()).birthDate(user.getBirthDate())
                .balance(acc != null ? acc.getBalance() : BigDecimal.ZERO)
                .currency(acc != null ? acc.getCurrency() : Currency.RUB).build();
    }
}
