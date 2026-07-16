package io.github.bud127.modulecomposer.sample.audit;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AuditModuleConfiguration.class)
public class AuditModuleConfiguration {
}
