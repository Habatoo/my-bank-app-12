package io.github.habatoo.services;

import io.github.habatoo.dto.enums.Currency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Клиентский сервис для получения курсов конвертации между валютами.
 * Использует {@link RateProviderService} для доступа к актуальным значениям котировок.
 */
@Slf4j
@RequiredArgsConstructor
public class RateClientService {

    private final RateProviderService rateProviderService;

    /**
     * Возвращает курс обмена для указанной пары валют.
     * * @param fromCurrency исходная валюта
     *
     * @param toCurrency целевая валюта
     * @return {@link BigDecimal} значение курса; возвращает 1, если валюты совпадают
     */
    public BigDecimal takeRate(Currency fromCurrency, Currency toCurrency) {
        if (fromCurrency == toCurrency) {
            return BigDecimal.ONE;
        }

        return rateProviderService.getRate(fromCurrency, toCurrency);
    }
}
