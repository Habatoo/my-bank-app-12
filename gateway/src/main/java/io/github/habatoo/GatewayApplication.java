package io.github.habatoo;

import io.github.habatoo.configurations.OutboxChassisAutoConfiguration;
import io.github.habatoo.configurations.ResilienceChassisAutoConfiguration;
import io.github.habatoo.configurations.ServicesChassisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        R2dbcAutoConfiguration.class,
        R2dbcRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        LiquibaseAutoConfiguration.class,
        ResilienceChassisAutoConfiguration.class,
        ServicesChassisAutoConfiguration.class,
        OutboxChassisAutoConfiguration.class
})
@ComponentScan(
        basePackages = "io.github.habatoo",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                io.github.habatoo.services.OutboxClientService.class,
                                io.github.habatoo.services.KafkaNotificationPublisher.class
                        }
                ),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "io\\.github\\.habatoo\\.repositories\\..*"
                )
        }
)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
