package io.github.habatoo.configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

    @Bean
    public Counter cashSentFailureCounter(MeterRegistry meterRegistry) {
        return Counter.builder("cash_failure_sent_total")
                .description("Количество не успешных пополнений")
                .tag("status", "failure")
                .register(meterRegistry);
    }
}
