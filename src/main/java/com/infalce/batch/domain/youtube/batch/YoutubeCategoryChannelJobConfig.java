package com.infalce.batch.domain.youtube.batch;

import com.infalce.batch.domain.youtube.api.YoutubeApiClient.YoutubeCategoryItem;
import com.infalce.batch.domain.youtube.api.YoutubeBatchProperties;
import com.infalce.batch.domain.youtube.batch.listener.YoutubeCategoryChannelJobExecutionListener;
import com.infalce.batch.domain.youtube.batch.listener.YoutubeCategoryChannelStepExecutionListener;
import com.infalce.batch.domain.youtube.batch.listener.model.YoutubeBatchStepExecutionContext;
import com.infalce.batch.domain.youtube.model.CategoryChannelDiscovery;
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
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(YoutubeBatchProperties.class)
public class YoutubeCategoryChannelJobConfig {

    public static final String JOB_NAME = "youtubeCategoryChannelJob";
    private static final int CHUNK_SIZE = 10;

    private final YoutubeCategoryChannelBatchService service;
    private final JobRepository jobRepository;
    private final YoutubeCategoryChannelJobExecutionListener jobExecutionListener;
    private final YoutubeCategoryChannelStepExecutionListener stepExecutionListener;
    private final YoutubeBatchProperties properties;

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
                .<YoutubeCategoryItem, YoutubeCategoryItem>chunk(CHUNK_SIZE, transactionManager)
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
                .<YoutubeCategory, CategoryChannelDiscovery>chunk(CHUNK_SIZE, transactionManager)
                .listener(stepExecutionListener)
                .listener(youtubeChannelDiscoveryPromotionListener)
                .reader(youtubeChannelDiscoveryReader)
                .processor(youtubeChannelDiscoveryProcessor)
                .writer(youtubeChannelDiscoveryWriter)
                .build();
    }

    @Bean
    public Step youtubeChannelMetricsRefreshStep(
            PlatformTransactionManager transactionManager,
            ItemStreamReader<Channel> youtubeChannelMetricsReader,
            ItemWriter<Channel> youtubeChannelMetricsWriter,
            ExecutionContextPromotionListener youtubeChannelMetricsPromotionListener
    ) {
        return new StepBuilder("youtubeChannelMetricsRefreshStep", jobRepository)
                .<Channel, Channel>chunk(CHUNK_SIZE, transactionManager)
                .listener(stepExecutionListener)
                .listener(youtubeChannelMetricsPromotionListener)
                .reader(youtubeChannelMetricsReader)
                .writer(youtubeChannelMetricsWriter)
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
    public ExecutionContextPromotionListener youtubeChannelMetricsPromotionListener() {
        return promotionListener(
                YoutubeBatchStepExecutionContext.CHANNEL_METRICS_CHANNEL_COUNT,
                YoutubeBatchStepExecutionContext.CHANNEL_METRICS_VIDEO_COUNT
        );
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
        JpaPagingItemReader<YoutubeCategory> reader = new JpaPagingItemReaderBuilder<YoutubeCategory>()
                .name("youtubeChannelDiscoveryReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select c from YoutubeCategory c order by c.youtubeCategoryId asc")
                .pageSize(CHUNK_SIZE)
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
            EntityManagerFactory entityManagerFactory
    ) {
        JpaPagingItemReader<Channel> reader = new JpaPagingItemReaderBuilder<Channel>()
                .name("youtubeChannelMetricsReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select c from Channel c where c.youtubeChannelId is not null order by c.id asc")
                .pageSize(CHUNK_SIZE)
                .build();
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
