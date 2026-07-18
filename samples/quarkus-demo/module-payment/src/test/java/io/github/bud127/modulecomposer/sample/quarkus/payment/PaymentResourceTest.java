package io.github.bud127.modulecomposer.sample.quarkus.payment;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;

@QuarkusTest
class PaymentResourceTest {

    @Test
    void healthReturnsPaymentModuleStatus() {
        given()
                .when()
                .get("/api/payment/health")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("UP"))
                .body("module", org.hamcrest.Matchers.equalTo("payment"))
                .body("time", not(blankOrNullString()));
    }
}
