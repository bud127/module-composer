package io.github.bud127.modulecomposer.sample.health;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = HealthModuleConfiguration.class)
public class HealthModuleConfiguration {
}
