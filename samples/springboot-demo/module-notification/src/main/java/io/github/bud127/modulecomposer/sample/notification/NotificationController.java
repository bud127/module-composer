package io.github.bud127.modulecomposer.sample.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class NotificationController {

    @GetMapping("/api/notification/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "module", "notification",
                "time", OffsetDateTime.now().toString()
        );
    }
}
