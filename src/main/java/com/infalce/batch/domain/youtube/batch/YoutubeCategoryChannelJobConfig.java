package com.infalce.batch.domain.youtube.batch;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeCategoryItem;
import com.infalce.batch.domain.youtube.api.YoutubeBatchProperties;
import com.infalce.batch.domain.youtube.batch.listener.YoutubeCategoryChannelJobExecutionListener;
import com.infalce.batch.domain.youtube.batch.listener.YoutubeCategoryChannelStepExecutionListener;
import com.infalce.batch.domain.youtube.batch.listener.model.YoutubeBatchStepExecutionContext;
import com.infalce.batch.domain.youtube.batch.reader.ChannelNoOffsetPagingItemReader;
import com.infalce.batch.domain.youtube.model.CategoryChannelDiscovery;
import com.infalce.batch.domain.youtube.repository.ChannelRepository;
import com.infalce.batch.domain.youtube.service.YoutubeCategoryChannelBatchService;
import com.infalce.batch.entity.channel.Channel;
import com.infalce.batch.entity.video.YoutubeCategory;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.*;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(YoutubeBatchProperties.class)
public class YoutubeCategoryChannelJobConfig {

    public static final String JOB_NAME = "youtubeCategoryChannelJob";
    private static final int DEFAULT_CHUNK_SIZE = 10;
    private static final int METRICS_CHUNK_SIZE = 50;
    private static final int METRICS_GRID_SIZE = 4;
    private static final String METRICS_WORKER_STEP_NAME = "youtubeChannelMetricsRefreshWorkerStep";

    private final YoutubeCategoryChannelBatchService service;
    private final JobRepository jobRepository;
    private final YoutubeCategoryChannelJobExecutionListener jobExecutionListener;
    private final YoutubeCategoryChannelStepExecutionListener stepExecutionListener;
    private final YoutubeBatchProperties properties;
    private final ChannelRepository channelRepository;

    @Bean
    public Job youtubeCategoryChannelJob(
            Step youtubeCategorySyncStep,
            Step youtubeChannelDiscoveryStep,
            Step youtubeChannelMetricsRefreshStep
    ) {
        SimpleJobBuilder builder = new JobBuilder(JOB_NAME, jobRepository)
                .listener(jobExecutionListener)
                .start(youtubeCategorySyncStep)
                .next(youtubeChannelDiscoveryStep)
                .next(youtubeChannelMetricsRefreshStep);
        return builder.build();
    }

    @Bean
    public Step youtubeCategorySyncStep(
            PlatformTransactionManager transactionManager,
            ItemReader<YoutubeCategoryItem> youtubeCategorySyncReader,
            ItemWriter<YoutubeCategoryItem> youtubeCategorySyncWriter,
            ExecutionContextPromotionListener youtubeCategorySyncPromotionListener
    ) {
        return new StepBuilder("youtubeCategorySyncStep", jobRepository)
                .<YoutubeCategoryItem, YoutubeCategoryItem>chunk(DEFAULT_CHUNK_SIZE, transactionManager)
                .listener(stepExecutionListener)
                .listener(youtubeCategorySyncPromotionListener)
                .reader(youtubeCategorySyncReader)
                .writer(youtubeCategorySyncWriter)
                .build();
    }

    @Bean
    public Step youtubeChannelDiscoveryStep(
            PlatformTransactionManager transactionManager,
            ItemStreamReader<YoutubeCategory> youtubeChannelDiscoveryReader,
            ItemProcessor<YoutubeCategory, CategoryChannelDiscovery> youtubeChannelDiscoveryProcessor,
            ItemWriter<CategoryChannelDiscovery> youtubeChannelDiscoveryWriter,
            ExecutionContextPromotionListener youtubeChannelDiscoveryPromotionListener
    ) {
        return new StepBuilder("youtubeChannelDiscoveryStep", jobRepository)
                .<YoutubeCategory, CategoryChannelDiscovery>chunk(DEFAULT_CHUNK_SIZE, transactionManager)
                .listener(stepExecutionListener)
                .listener(youtubeChannelDiscoveryPromotionListener)
                .reader(youtubeChannelDiscoveryReader)
                .processor(youtubeChannelDiscoveryProcessor)
                .writer(youtubeChannelDiscoveryWriter)
                .build();
    }

    @Bean
    public Step youtubeChannelMetricsRefreshStep(
            Step youtubeChannelMetricsRefreshWorkerStep,
            TaskExecutor youtubeChannelMetricsTaskExecutor
    ) {
        return new StepBuilder("youtubeChannelMetricsRefreshStep", jobRepository)
                .partitioner(METRICS_WORKER_STEP_NAME, youtubeChannelMetricsPartitioner())
                .step(youtubeChannelMetricsRefreshWorkerStep)
                .gridSize(METRICS_GRID_SIZE)
                .taskExecutor(youtubeChannelMetricsTaskExecutor)
                .listener(stepExecutionListener)
                .build();
    }

    @Bean
    public ExecutionContextPromotionListener youtubeCategorySyncPromotionListener() {
        return promotionListener(
                YoutubeBatchStepExecutionContext.CATEGORY_SYNC_CHANGED_COUNT,
                YoutubeBatchStepExecutionContext.CATEGORY_SYNC_NEW_COUNT
        );
    }

    @Bean
    public ExecutionContextPromotionListener youtubeChannelDiscoveryPromotionListener() {
        return promotionListener(
                YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_CATEGORY_COUNT,
                YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_DISCOVERED_CHANNEL_COUNT
        );
    }

    @Bean
    public Step youtubeChannelMetricsRefreshWorkerStep(
            PlatformTransactionManager transactionManager,
            ItemStreamReader<Channel> youtubeChannelMetricsReader,
            ItemWriter<Channel> youtubeChannelMetricsWriter
    ) {
        return new StepBuilder(METRICS_WORKER_STEP_NAME, jobRepository)
                .<Channel, Channel>chunk(METRICS_CHUNK_SIZE, transactionManager)
                .listener(stepExecutionListener)
                .reader(youtubeChannelMetricsReader)
                .writer(youtubeChannelMetricsWriter)
                .build();
    }

    @Bean
    public TaskExecutor youtubeChannelMetricsTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setThreadNamePrefix("youtube-metrics-");
        taskExecutor.setCorePoolSize(METRICS_GRID_SIZE);
        taskExecutor.setMaxPoolSize(METRICS_GRID_SIZE);
        taskExecutor.setQueueCapacity(0);
        taskExecutor.initialize();
        return taskExecutor;
    }

    @Bean
    public Partitioner youtubeChannelMetricsPartitioner() {
        return gridSize -> {
            Long minId = channelRepository.findMinIdByYoutubeChannelIdIsNotNull();
            Long maxId = channelRepository.findMaxIdByYoutubeChannelIdIsNotNull();

            Map<String, ExecutionContext> partitions = new HashMap<>();
            if (minId == null || maxId == null) {
                ExecutionContext empty = new ExecutionContext();
                empty.putLong("minId", 1L);
                empty.putLong("maxId", 0L);
                partitions.put("partition0", empty);
                return partitions;
            }

            long targetGridSize = Math.max(1, gridSize);
            long range = (maxId - minId) + 1;
            long partitionSize = Math.max(1, (long) Math.ceil((double) range / targetGridSize));

            long start = minId;
            int partitionIndex = 0;
            while (start <= maxId) {
                long end = Math.min(start + partitionSize - 1, maxId);
                ExecutionContext executionContext = new ExecutionContext();
                executionContext.putLong("minId", start);
                executionContext.putLong("maxId", end);
                partitions.put("partition" + partitionIndex, executionContext);
                start = end + 1;
                partitionIndex++;
            }
            return partitions;
        };
    }

    @Bean
    @StepScope
    public ItemReader<YoutubeCategoryItem> youtubeCategorySyncReader(
            @Value("#{jobParameters['regionCode']}") String regionCode,
            @Value("#{jobParameters['hl']}") String hl
    ) {
        return new ListItemReader<>(service.loadYoutubeCategories(
                resolveStringJobParameter(regionCode, properties.getRegionCode()),
                resolveStringJobParameter(hl, properties.getHl())
        ));
    }

    @Bean
    public ItemWriter<YoutubeCategoryItem> youtubeCategorySyncWriter() {
        return chunk -> {
            var summary = service.upsertCategories(new ArrayList<>(chunk.getItems()));
            YoutubeBatchStepExecutionContext.addLong(
                    YoutubeBatchStepExecutionContext.CATEGORY_SYNC_CHANGED_COUNT,
                    summary.changedCount()
            );
            YoutubeBatchStepExecutionContext.addLong(
                    YoutubeBatchStepExecutionContext.CATEGORY_SYNC_NEW_COUNT,
                    summary.newCount()
            );
        };
    }

    @Bean
    @StepScope
    public ItemStreamReader<YoutubeCategory> youtubeChannelDiscoveryReader(
            EntityManagerFactory entityManagerFactory
    ) {
        var reader = new JpaPagingItemReaderBuilder<YoutubeCategory>()
                .name("youtubeChannelDiscoveryReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select c from YoutubeCategory c order by c.youtubeCategoryId asc")
                .pageSize(DEFAULT_CHUNK_SIZE)
                .build();
        reader.setSaveState(false);
        return reader;
    }

    @Bean
    @StepScope
    public ItemProcessor<YoutubeCategory, CategoryChannelDiscovery> youtubeChannelDiscoveryProcessor(
            @Value("#{jobParameters['regionCode']}") String regionCode,
            @Value("#{jobParameters['minChannelCount']}") Long minChannelCount
    ) {
        return category -> service.collectCategoryChannelDiscovery(
                category,
                resolveStringJobParameter(regionCode, properties.getRegionCode()),
                resolveIntJobParameter(minChannelCount, properties.getMinChannelCount())
        );
    }

    @Bean
    public ItemWriter<CategoryChannelDiscovery> youtubeChannelDiscoveryWriter() {
        return chunk -> {
            var summary = service.writeDiscoveredChannels(new ArrayList<>(chunk.getItems()));
            YoutubeBatchStepExecutionContext.addLong(
                    YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_CATEGORY_COUNT,
                    summary.categoryCount()
            );
            YoutubeBatchStepExecutionContext.addLong(
                    YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_DISCOVERED_CHANNEL_COUNT,
                    summary.discoveredChannelCount()
            );
        };
    }

    @Bean
    @StepScope
    public ItemStreamReader<Channel> youtubeChannelMetricsReader(
            EntityManagerFactory entityManagerFactory,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId
    ) {
        ChannelNoOffsetPagingItemReader reader = new ChannelNoOffsetPagingItemReader(
                entityManagerFactory,
                METRICS_CHUNK_SIZE,
                minId != null ? minId : 1L,
                maxId != null ? maxId : 0L
        );
        reader.setName("youtubeChannelMetricsReader");
        reader.setSaveState(false);
        return reader;
    }

    @Bean
    @StepScope
    public ItemWriter<Channel> youtubeChannelMetricsWriter(
            @Value("#{jobParameters['recentDays']}") Long recentDays
    ) {
        return chunk -> {
            var summary = service.refreshChannelMetrics(
                    new ArrayList<>(chunk.getItems()),
                    resolveIntJobParameter(recentDays, properties.getRecentDays())
            );
            YoutubeBatchStepExecutionContext.addLong(
                    YoutubeBatchStepExecutionContext.CHANNEL_METRICS_CHANNEL_COUNT,
                    summary.channelCount()
            );
            YoutubeBatchStepExecutionContext.addLong(
                    YoutubeBatchStepExecutionContext.CHANNEL_METRICS_VIDEO_COUNT,
                    summary.videoCount()
            );
        };
    }

    private ExecutionContextPromotionListener promotionListener(String... keys) {
        ExecutionContextPromotionListener listener = new ExecutionContextPromotionListener();
        listener.setKeys(keys);
        return listener;
    }

    private String resolveStringJobParameter(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private int resolveIntJobParameter(Long value, int defaultValue) {
        return value != null ? Math.toIntExact(value) : defaultValue;
    }
}
