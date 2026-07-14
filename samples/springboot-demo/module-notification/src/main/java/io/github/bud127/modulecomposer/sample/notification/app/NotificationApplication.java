package io.github.bud127.modulecomposer.sample.notification.app;

import io.github.bud127.modulecomposer.sample.notification.NotificationModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.util.Map;

@SpringBootApplication(scanBasePackages = "io.github.bud127.modulecomposer.sample.notification.app")
@Import({
        NotificationModuleConfiguration.class
})
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication application =
                new SpringApplication(NotificationApplication.class);

        application.setDefaultProperties(
                Map.of(
                        "spring.application.name", "notification-app",
                        "server.port", "8082",
                        "composer.modules", "notification"
                )
        );

        application.run(args);
    }
}
