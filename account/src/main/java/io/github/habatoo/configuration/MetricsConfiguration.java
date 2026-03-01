package io.github.habatoo.configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

    @Bean
    public Counter balanceSuccessCounter(MeterRegistry meterRegistry) {
        return Counter.builder("account_balance_change_total")
                .description("Успешные изменения баланса")
                .tag("status", "success")
                .register(meterRegistry);
    }

    @Bean
    public Counter balanceFailureCounter(MeterRegistry meterRegistry) {
        return Counter.builder("account_balance_change_total")
                .description("Ошибки изменения баланса")
                .tag("status", "failure")
                .register(meterRegistry);
    }

    @Bean
    public Counter accountOpenCounter(MeterRegistry meterRegistry) {
        return Counter.builder("account_open_total")
                .description("Создание счетов")
                .register(meterRegistry);
    }

    @Bean
    public Timer balanceChangeTimer(MeterRegistry meterRegistry) {
        return Timer.builder("account_balance_change_duration_seconds")
                .description("Время изменения баланса")
                .register(meterRegistry);
    }

    @Bean
    public Counter userCreatedSuccessCounter(MeterRegistry meterRegistry) {
        return Counter.builder("account_user_created_total")
                .description("Количество созданных пользователей")
                .tag("status", "success")
                .register(meterRegistry);
    }

    @Bean
    public Counter userCreatedFailureCounter(MeterRegistry meterRegistry) {
        return Counter.builder("account_user_created_total")
                .description("Количество созданных пользователей")
                .tag("status", "failure")
                .register(meterRegistry);
    }
}
