package io.github.bud127.modulecomposer.sample.audit;

import io.github.bud127.modulecomposer.sample.audit.app.AuditApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuditApplication.class)
@AutoConfigureMockMvc
class AuditApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsAuditModuleStatus() throws Exception {
        mockMvc.perform(get("/api/audit/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.module").value("audit"))
                .andExpect(jsonPath("$.time", not(blankOrNullString())));
    }
}
