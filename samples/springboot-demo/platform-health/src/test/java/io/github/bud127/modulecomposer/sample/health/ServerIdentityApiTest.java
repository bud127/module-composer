package io.github.bud127.modulecomposer.sample.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ServerIdentityApiTest.TestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.application.name=platform-health-test",
        "composer.modules=platform-health",
        "composer.distribution=test-distribution",
        "SERVER_HOSTNAME=test-host",
        "SERVER_ID=test-server",
        "SERVER_IP=127.0.0.10"
})
class ServerIdentityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void serverReturnsPlatformIdentity() throws Exception {
        mockMvc.perform(get("/api/server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("platform-health-test"))
                .andExpect(jsonPath("$.serverId").value("test-server"))
                .andExpect(jsonPath("$.hostname").value("test-host"))
                .andExpect(jsonPath("$.ip").value("127.0.0.10"))
                .andExpect(jsonPath("$.modules", hasItem("platform-health")))
                .andExpect(jsonPath("$.distribution").value("test-distribution"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ServerIdentityController.class)
    static class TestApplication {
    }
}
