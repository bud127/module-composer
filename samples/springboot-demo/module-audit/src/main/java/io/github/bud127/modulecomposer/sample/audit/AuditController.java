package io.github.bud127.modulecomposer.sample.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AuditController {

    @GetMapping("/api/audit/health")
    public Map<String, Object> health() throws Exception  {
        InetAddress localHost = InetAddress.getLocalHost();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("module", "audit");
        response.put("hostname", localHost.getHostName());
        response.put("ipAddress", localHost.getHostAddress());
        response.put("pid", ManagementFactory.getRuntimeMXBean().getPid());
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("time", OffsetDateTime.now());
        return response;
    }
}
