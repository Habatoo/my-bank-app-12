package io.github.habatoo;

import io.github.habatoo.configurations.SecurityChassisAutoConfiguration;
import io.github.habatoo.configurations.WebClientChassisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication(exclude = {
        SecurityChassisAutoConfiguration.class,
        WebClientChassisAutoConfiguration.class
})
@EnableR2dbcRepositories(basePackages = "io.github.habatoo.repositories")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
