package com.infalce.batch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "youtube.batch.schedule", name = "enabled", havingValue = "true")
public class SchedulingConfig {
}
