package com.infalce.batch.domain.email.batch;

import com.infalce.batch.domain.email.batch.reader.ClaimedEmailOutboxNoOffsetPagingItemReader;
import com.infalce.batch.domain.email.batch.reader.EmailOutboxNoOffsetPagingItemReader;
import com.infalce.batch.domain.email.batch.writer.EmailOutboxRelayItemWriter;
import com.infalce.batch.domain.email.service.EmailSender;
import com.infalce.batch.entity.email.EmailOutboxEvent;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class EmailOutboxRelayJobConfig {

    public static final String JOB_NAME = "emailOutboxRelayJob";

    private static final int DEFAULT_BATCH_SIZE = 20;

    private final JobRepository jobRepository;
    private final EmailSender emailSender;

    @Bean
    public Job emailOutboxRelayJob(
            @Qualifier("emailOutboxClaimStep") Step emailOutboxClaimStep,
            @Qualifier("emailOutboxRelayStep") Step emailOutboxRelayStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(emailOutboxClaimStep)
                .next(emailOutboxRelayStep)
                .build();
    }

    @Bean
    public Step emailOutboxClaimStep(
            PlatformTransactionManager transactionManager,
            @Qualifier("emailOutboxClaimReader") ItemReader<EmailOutboxEvent> emailOutboxClaimReader,
            @Qualifier("emailOutboxClaimProcessor") ItemProcessor<EmailOutboxEvent, EmailOutboxEvent> emailOutboxClaimProcessor,
            @Qualifier("emailOutboxClaimWriter") ItemWriter<EmailOutboxEvent> emailOutboxClaimWriter
    ) {
        return new StepBuilder("emailOutboxClaimStep", jobRepository)
                .<EmailOutboxEvent, EmailOutboxEvent>chunk(DEFAULT_BATCH_SIZE, transactionManager)
                .reader(emailOutboxClaimReader)
                .processor(emailOutboxClaimProcessor)
                .writer(emailOutboxClaimWriter)
                .build();
    }

    @Bean
    public Step emailOutboxRelayStep(
            PlatformTransactionManager transactionManager,
            @Qualifier("emailOutboxRelayReader") ItemReader<EmailOutboxEvent> emailOutboxRelayReader,
            @Qualifier("emailOutboxRelayWriter") ItemWriter<EmailOutboxEvent> emailOutboxRelayWriter
    ) {
        return new StepBuilder("emailOutboxRelayStep", jobRepository)
                .<EmailOutboxEvent, EmailOutboxEvent>chunk(DEFAULT_BATCH_SIZE, transactionManager)
                .reader(emailOutboxRelayReader)
                .writer(emailOutboxRelayWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<EmailOutboxEvent> emailOutboxClaimReader(
            EntityManagerFactory entityManagerFactory,
            @Value("#{jobParameters['maxAttemptCount']}") Long maxAttemptCount,
            @Value("#{jobParameters['batchSize']}") Long batchSize
    ) {
        int resolvedBatchSize = Math.toIntExact(batchSize);
        EmailOutboxNoOffsetPagingItemReader reader = new EmailOutboxNoOffsetPagingItemReader(
                entityManagerFactory,
                Math.min(resolvedBatchSize, DEFAULT_BATCH_SIZE),
                Math.toIntExact(maxAttemptCount),
                resolvedBatchSize
        );
        reader.setName("emailOutboxClaimReader");
        reader.setSaveState(false);
        return reader;
    }

    @Bean
    @StepScope
    public ItemProcessor<EmailOutboxEvent, EmailOutboxEvent> emailOutboxClaimProcessor(
            @Value("#{jobParameters['claimStartedAt']}") String claimStartedAt
    ) {
        LocalDateTime claimedAt = LocalDateTime.parse(claimStartedAt);
        return event -> {
            event.publish(claimedAt);
            return event;
        };
    }

    @Bean
    public ItemWriter<EmailOutboxEvent> emailOutboxClaimWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<EmailOutboxEvent>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<EmailOutboxEvent> emailOutboxRelayReader(
            EntityManagerFactory entityManagerFactory,
            @Value("#{jobParameters['batchSize']}") Long batchSize,
            @Value("#{jobParameters['claimStartedAt']}") String claimStartedAt
    ) {
        int resolvedBatchSize = Math.toIntExact(batchSize);
        ClaimedEmailOutboxNoOffsetPagingItemReader reader = new ClaimedEmailOutboxNoOffsetPagingItemReader(
                entityManagerFactory,
                Math.min(resolvedBatchSize, DEFAULT_BATCH_SIZE),
                resolvedBatchSize,
                LocalDateTime.parse(claimStartedAt)
        );
        reader.setName("emailOutboxRelayReader");
        reader.setSaveState(false);
        return reader;
    }

    @Bean
    public ItemWriter<EmailOutboxEvent> emailOutboxRelayWriter(
            EntityManagerFactory entityManagerFactory,
            @Qualifier("emailOutboxRelayTaskExecutor") TaskExecutor emailOutboxRelayTaskExecutor
    ) {
        ItemWriter<EmailOutboxEvent> delegate = new JpaItemWriterBuilder<EmailOutboxEvent>()
                .entityManagerFactory(entityManagerFactory)
                .build();
        return new EmailOutboxRelayItemWriter(emailSender, delegate, emailOutboxRelayTaskExecutor);
    }

    @Bean
    public TaskExecutor emailOutboxRelayTaskExecutor(
            @Value("${email.outbox.relay.concurrency}") int concurrency
    ) {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setThreadNamePrefix("email-outbox-relay-");
        taskExecutor.setCorePoolSize(concurrency);
        taskExecutor.setMaxPoolSize(concurrency);
        taskExecutor.setQueueCapacity(DEFAULT_BATCH_SIZE);
        taskExecutor.initialize();
        return taskExecutor;
    }
}
