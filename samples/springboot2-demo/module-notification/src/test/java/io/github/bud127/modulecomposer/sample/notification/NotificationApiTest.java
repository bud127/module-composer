package io.github.bud127.modulecomposer.sample.notification;

import io.github.bud127.modulecomposer.sample.notification.app.NotificationApplication;
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

@SpringBootTest(classes = NotificationApplication.class)
@AutoConfigureMockMvc
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsNotificationModuleStatus() throws Exception {
        mockMvc.perform(get("/api/notification/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.module").value("notification"))
                .andExpect(jsonPath("$.time", not(blankOrNullString())));
    }
}
