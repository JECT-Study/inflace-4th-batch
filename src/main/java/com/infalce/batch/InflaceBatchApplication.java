package com.infalce.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class InflaceBatchApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(InflaceBatchApplication.class, args);
        boolean youtubeScheduleEnabled = context.getEnvironment()
                .getProperty("youtube.batch.schedule.enabled", Boolean.class, false);
        boolean emailOutboxRelayScheduleEnabled = context.getEnvironment()
                .getProperty("email.outbox.relay.schedule.enabled", Boolean.class, false);
        if (!youtubeScheduleEnabled && !emailOutboxRelayScheduleEnabled) {
            int exitCode = SpringApplication.exit(context);
            System.exit(exitCode);
        }
    }

}
