package io.github.bud127.modulecomposer.sample.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class PaymentController {

    @GetMapping("/api/payment/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "module", "payment",
                "time", OffsetDateTime.now().toString()
        );
    }
}
