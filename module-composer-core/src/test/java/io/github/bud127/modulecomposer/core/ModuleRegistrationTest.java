package io.github.bud127.modulecomposer.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleRegistrationTest {

    @Test
    void storesModuleMetadata() {
        ModuleRegistration module = new ModuleRegistration(
                "payment",
                ":module-payment",
                "example.PaymentConfiguration",
                ":module-payment:bootRun",
                ":module-payment:bootJar",
                ":module-payment:jar"
        );

        assertEquals("payment", module.name());
        assertEquals(":module-payment:bootRun", module.standaloneRunTask());
        assertEquals(":module-payment:jar", module.plainJarTask());
    }
}
