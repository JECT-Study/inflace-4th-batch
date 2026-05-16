package com.infalce.batch.domain.email.scheduler;

import com.infalce.batch.domain.email.batch.EmailOutboxRelayJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "email.outbox.relay.schedule", name = "enabled", havingValue = "true")
public class EmailOutboxRelayJobScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;

    @Qualifier(EmailOutboxRelayJobConfig.JOB_NAME)
    private final Job emailOutboxRelayJob;

    @Value("${email.outbox.relay.max-attempt-count}")
    private int maxAttemptCount;

    @Value("${email.outbox.relay.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${email.outbox.relay.schedule.fixed-delay-ms}")
    public void launch() {
        Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(EmailOutboxRelayJobConfig.JOB_NAME);
        if (!runningExecutions.isEmpty()) {
            log.info("email outbox relay batch job is already running. runningExecutionCount: {}", runningExecutions.size());
            return;
        }

        LocalDateTime claimStartedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        JobParameters parameters = new JobParametersBuilder()
                .addLong("maxAttemptCount", (long) maxAttemptCount)
                .addLong("batchSize", (long) batchSize)
                .addString("claimStartedAt", claimStartedAt.toString())
                .addString("requestedAt", Instant.now().toString())
                .toJobParameters();

        try {
            jobLauncher.run(emailOutboxRelayJob, parameters);
        } catch (Exception e) {
            log.error("email outbox relay batch job launch failed", e);
        }
    }
}
