package io.github.bud127.modulecomposer.sample.health;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ServerIdentityController {

    private final Environment environment;

    public ServerIdentityController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/api/server")
    public Map<String, Object> server() {
        String hostname = firstNonBlank(
                environment.getProperty("SERVER_HOSTNAME"),
                environment.getProperty("HOSTNAME"),
                resolveHostname()
        );

        String serverId = firstNonBlank(
                environment.getProperty("SERVER_ID"),
                hostname
        );

        String ip = firstNonBlank(
                environment.getProperty("SERVER_IP"),
                environment.getProperty("server.static-ip"),
                resolveIp()
        );

        var modules = Arrays.stream(
                environment.getProperty(
                        "composer.modules",
                        "unknown"
                ).split(",")
        )
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put(
                "application",
                environment.getProperty(
                        "spring.application.name",
                        "unknown"
                )
        );
        response.put("serverId", serverId);
        response.put("hostname", hostname);
        response.put("ip", ip);
        response.put("processId", ProcessHandle.current().pid());
        response.put("modules", modules);
        response.put(
                "distribution",
                environment.getProperty(
                        "composer.distribution",
                        ""
                )
        );

        return response;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown-host";
        }
    }

    private static String resolveIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            return "unknown-ip";
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "unknown";
    }
}
