package io.github.bud127.modulecomposer.sample.quarkus.payment;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/payment")
public class PaymentResource {

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> health() throws Exception {
        InetAddress localHost = InetAddress.getLocalHost();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("module", "payment");
        response.put("hostname", localHost.getHostName());
        response.put("ipAddress", localHost.getHostAddress());
        response.put("pid", ManagementFactory.getRuntimeMXBean().getPid());
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("time", OffsetDateTime.now().toString());
        return response;
    }
}
