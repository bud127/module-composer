package io.github.bud127.modulecomposer.sample.payment.app;

import io.github.bud127.modulecomposer.sample.payment.PaymentModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.util.Map;

@SpringBootApplication(scanBasePackages = "io.github.bud127.modulecomposer.sample.payment.app")
@Import({
        PaymentModuleConfiguration.class
})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication application =
                new SpringApplication(PaymentApplication.class);

        application.setDefaultProperties(
                Map.of(
                        "spring.application.name", "payment-app",
                        "server.port", "8081",
                        "composer.modules", "payment"
                )
        );

        application.run(args);
    }
}
