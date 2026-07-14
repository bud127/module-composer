package io.github.bud127.modulecomposer.sample.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class AuditController {

    @GetMapping("/api/audit/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "module", "audit",
                "time", OffsetDateTime.now().toString()
        );
    }
}
