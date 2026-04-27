package com.infalce.batch.domain.youtube.batch.listener;

import com.infalce.batch.domain.youtube.batch.listener.model.YoutubeBatchStepExecutionContext;
import com.infalce.batch.domain.youtube.batch.notification.YoutubeBatchDiscordNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeCategoryChannelJobExecutionListener implements JobExecutionListener {

    private final YoutubeBatchDiscordNotifier discordNotifier;

    @Override
    public void afterJob(JobExecution jobExecution) {
        long categorySyncChangedCount = readLong(jobExecution, YoutubeBatchStepExecutionContext.CATEGORY_SYNC_CHANGED_COUNT);
        long categorySyncNewCount = readLong(jobExecution, YoutubeBatchStepExecutionContext.CATEGORY_SYNC_NEW_COUNT);
        long channelDiscoveryCategoryCount = readLong(jobExecution, YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_CATEGORY_COUNT);
        long channelDiscoveryDiscoveredChannelCount = readLong(jobExecution, YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_DISCOVERED_CHANNEL_COUNT);
        long channelMetricsChannelCount = sumWorkerStepLong(jobExecution, YoutubeBatchStepExecutionContext.CHANNEL_METRICS_CHANNEL_COUNT);
        long channelMetricsVideoCount = sumWorkerStepLong(jobExecution, YoutubeBatchStepExecutionContext.CHANNEL_METRICS_VIDEO_COUNT);
        String duration = formatDuration(jobExecution);

        String stepSummary = logicalStepExecutions(jobExecution).stream()
                .sorted(Comparator.comparing(StepExecution::getStepName))
                .map(stepExecution -> stepName(stepExecution.getStepName()) + ": " + status(stepExecution.getStatus().toString()))
                .collect(Collectors.joining("\n"));
        String executionSummary = """
                총 실행 시간: %s
                카테고리 변경 수: %d
                신규 카테고리 수: %d
                채널 탐색 카테고리 수: %d
                발견한 채널 수: %d
                메트릭 갱신 채널 수: %d
                메트릭 수집 영상 수: %d
                """.formatted(
                duration,
                categorySyncChangedCount,
                categorySyncNewCount,
                channelDiscoveryCategoryCount,
                channelDiscoveryDiscoveredChannelCount,
                channelMetricsChannelCount,
                channelMetricsVideoCount
        );

        log.info(
                "batch job finished. job={} status={} duration={} stepSummary=[{}] categorySyncChangedCount={} categorySyncNewCount={} channelDiscoveryCategoryCount={} channelDiscoveryDiscoveredChannelCount={} channelMetricsChannelCount={} channelMetricsVideoCount={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                duration,
                stepSummary,
                categorySyncChangedCount,
                categorySyncNewCount,
                channelDiscoveryCategoryCount,
                channelDiscoveryDiscoveredChannelCount,
                channelMetricsChannelCount,
                channelMetricsVideoCount
        );
        discordNotifier.notifyJobFinished(jobExecution, stepSummary, executionSummary);
    }

    private long readLong(JobExecution jobExecution, String key) {
        return jobExecution.getExecutionContext().containsKey(key)
                ? jobExecution.getExecutionContext().getLong(key)
                : 0L;
    }

    private long sumWorkerStepLong(JobExecution jobExecution, String key) {
        return jobExecution.getStepExecutions().stream()
                .filter(stepExecution -> stepExecution.getStepName().startsWith("youtubeChannelMetricsRefreshWorkerStep"))
                .mapToLong(stepExecution -> stepExecution.getExecutionContext().containsKey(key)
                        ? stepExecution.getExecutionContext().getLong(key)
                        : 0L)
                .sum();
    }

    private List<StepExecution> logicalStepExecutions(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
                .filter(stepExecution -> !stepExecution.getStepName().startsWith("youtubeChannelMetricsRefreshWorkerStep"))
                .toList();
    }

    private String stepName(String stepName) {
        return switch (stepName) {
            case "youtubeCategorySyncStep" -> "카테고리 동기화";
            case "youtubeChannelDiscoveryStep" -> "채널 탐색";
            case "youtubeChannelMetricsRefreshStep" -> "채널 메트릭 갱신";
            default -> stepName;
        };
    }

    private String status(String status) {
        return switch (status) {
            case "COMPLETED" -> "완료";
            case "FAILED" -> "실패";
            case "STARTED" -> "시작됨";
            case "STARTING" -> "시작 중";
            case "STOPPED" -> "중지";
            case "STOPPING" -> "중지 중";
            case "UNKNOWN" -> "알 수 없음";
            default -> status;
        };
    }

    private String formatDuration(JobExecution jobExecution) {
        if (jobExecution.getStartTime() == null || jobExecution.getEndTime() == null) {
            return "-";
        }

        Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
        long totalSeconds = duration.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0) {
            return minutes + "분 " + seconds + "초";
        }
        return seconds + "초";
    }
}
