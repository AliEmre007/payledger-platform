package com.payledger.platform.shared.resilience;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceConfiguration {
}
