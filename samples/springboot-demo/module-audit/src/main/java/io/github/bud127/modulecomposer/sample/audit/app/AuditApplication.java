package io.github.bud127.modulecomposer.sample.audit.app;

import io.github.bud127.modulecomposer.sample.health.HealthModuleConfiguration;
import io.github.bud127.modulecomposer.sample.audit.AuditModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.util.Map;

@SpringBootApplication(scanBasePackages = "io.github.bud127.modulecomposer.sample.audit.app")
@Import({
        HealthModuleConfiguration.class,
        AuditModuleConfiguration.class
})
public class AuditApplication {

    public static void main(String[] args) {
        SpringApplication application =
                new SpringApplication(AuditApplication.class);

        application.setDefaultProperties(
                Map.of(
                        "spring.application.name", "audit-app",
                        "server.port", "8083",
                        "composer.modules", "audit"
                )
        );

        application.run(args);
    }
}
