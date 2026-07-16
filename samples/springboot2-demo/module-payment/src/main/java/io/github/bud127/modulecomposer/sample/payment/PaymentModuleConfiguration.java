package io.github.bud127.modulecomposer.sample.payment;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = PaymentModuleConfiguration.class)
public class PaymentModuleConfiguration {
}
