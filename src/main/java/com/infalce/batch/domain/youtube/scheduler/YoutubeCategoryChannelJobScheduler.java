package com.infalce.batch.domain.youtube.scheduler;

import com.infalce.batch.domain.youtube.api.YoutubeBatchProperties;
import com.infalce.batch.domain.youtube.batch.YoutubeCategoryChannelJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "youtube.batch.schedule", name = "enabled", havingValue = "true")
public class YoutubeCategoryChannelJobScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier(YoutubeCategoryChannelJobConfig.JOB_NAME)
    private final Job youtubeCategoryChannelJob;

    private final YoutubeBatchProperties properties;

    @Scheduled(
            cron = "${youtube.batch.schedule.cron}",
            zone = "${youtube.batch.schedule.zone}"
    )
    public void launch() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("regionCode", properties.getRegionCode())
                .addString("hl", properties.getHl())
                .addLong("minChannelCount", (long) properties.getMinChannelCount())
                .addLong("recentDays", (long) properties.getRecentDays())
                .addString("requestedAt", Instant.now().toString())
                .toJobParameters();

        try {
            jobLauncher.run(youtubeCategoryChannelJob, parameters);
        } catch (Exception e) {
            log.error("youtube category/channel batch job launch failed", e);
        }
    }
}
