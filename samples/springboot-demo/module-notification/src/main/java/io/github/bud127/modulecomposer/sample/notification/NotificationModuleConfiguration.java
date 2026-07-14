package io.github.bud127.modulecomposer.sample.notification;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = NotificationModuleConfiguration.class)
public class NotificationModuleConfiguration {
}
