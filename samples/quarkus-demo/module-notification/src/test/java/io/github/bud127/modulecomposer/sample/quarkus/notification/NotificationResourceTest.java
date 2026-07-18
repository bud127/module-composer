package io.github.bud127.modulecomposer.sample.quarkus.notification;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;

@QuarkusTest
class NotificationResourceTest {

    @Test
    void healthReturnsNotificationModuleStatus() {
        given()
                .when()
                .get("/api/notification/health")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("UP"))
                .body("module", org.hamcrest.Matchers.equalTo("notification"))
                .body("time", not(blankOrNullString()));
    }
}
