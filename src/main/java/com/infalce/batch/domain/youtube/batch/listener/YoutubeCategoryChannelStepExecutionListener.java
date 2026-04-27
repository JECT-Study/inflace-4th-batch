package com.infalce.batch.domain.youtube.batch.listener;

import com.infalce.batch.domain.youtube.batch.listener.model.YoutubeBatchStepExecutionContext;
import com.infalce.batch.domain.youtube.batch.notification.YoutubeBatchDiscordNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeCategoryChannelStepExecutionListener implements StepExecutionListener {

    private final YoutubeBatchDiscordNotifier discordNotifier;

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String customSummary = resolveCustomSummary(stepExecution);
        String duration = formatDuration(stepExecution);

        if (stepExecution.getStatus() == BatchStatus.FAILED) {
            String failureMessages = formatFailureMessages(stepExecution);
            log.error(
                    "batch step failed. step={} status={} exitStatus={} duration={} readCount={} writeCount={} filterCount={} readSkipCount={} processSkipCount={} writeSkipCount={} commitCount={} rollbackCount={} failureCount={} failureMessages=[{}]{}",
                    stepExecution.getStepName(),
                    stepExecution.getStatus(),
                    stepExecution.getExitStatus().getExitCode(),
                    duration,
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getFilterCount(),
                    stepExecution.getReadSkipCount(),
                    stepExecution.getProcessSkipCount(),
                    stepExecution.getWriteSkipCount(),
                    stepExecution.getCommitCount(),
                    stepExecution.getRollbackCount(),
                    stepExecution.getFailureExceptions().size(),
                    failureMessages,
                    customSummary
            );
            discordNotifier.notifyStepFailure(stepExecution, customSummary, failureMessages, duration);
        } else {
            log.info(
                    "batch step finished. step={} status={} duration={} readCount={} writeCount={} filterCount={} readSkipCount={} processSkipCount={} writeSkipCount={} commitCount={} rollbackCount={}{}",
                    stepExecution.getStepName(),
                    stepExecution.getStatus(),
                    duration,
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getFilterCount(),
                    stepExecution.getReadSkipCount(),
                    stepExecution.getProcessSkipCount(),
                    stepExecution.getWriteSkipCount(),
                    stepExecution.getCommitCount(),
                    stepExecution.getRollbackCount(),
                    customSummary
            );
        }
        return stepExecution.getExitStatus();
    }

    private String resolveCustomSummary(StepExecution stepExecution) {
        return switch (stepExecution.getStepName()) {
            case "youtubeCategorySyncStep" -> formatCategorySyncSummary(stepExecution);
            case "youtubeChannelDiscoveryStep" -> formatChannelDiscoverySummary(stepExecution);
            default -> stepExecution.getStepName().startsWith("youtubeChannelMetricsRefreshWorkerStep")
                    ? formatChannelMetricsSummary(stepExecution)
                    : "";
        };
    }

    private String formatCategorySyncSummary(StepExecution stepExecution) {
        long changedCount = readLong(stepExecution, YoutubeBatchStepExecutionContext.CATEGORY_SYNC_CHANGED_COUNT);
        long newCount = readLong(stepExecution, YoutubeBatchStepExecutionContext.CATEGORY_SYNC_NEW_COUNT);
        return " 변경 카테고리 수=" + changedCount + " 신규 카테고리 수=" + newCount;
    }

    private String formatChannelDiscoverySummary(StepExecution stepExecution) {
        long categoryCount = readLong(stepExecution, YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_CATEGORY_COUNT);
        long discoveredChannelCount = readLong(stepExecution, YoutubeBatchStepExecutionContext.CHANNEL_DISCOVERY_DISCOVERED_CHANNEL_COUNT);
        return " 탐색 카테고리 수=" + categoryCount + " 발견한 채널 수=" + discoveredChannelCount;
    }

    private String formatChannelMetricsSummary(StepExecution stepExecution) {
        long channelCount = readLong(stepExecution, YoutubeBatchStepExecutionContext.CHANNEL_METRICS_CHANNEL_COUNT);
        long videoCount = readLong(stepExecution, YoutubeBatchStepExecutionContext.CHANNEL_METRICS_VIDEO_COUNT);
        return " 메트릭 갱신 채널 수=" + channelCount + " 메트릭 수집 영상 수=" + videoCount;
    }

    private long readLong(StepExecution stepExecution, String key) {
        return stepExecution.getExecutionContext().containsKey(key)
                ? stepExecution.getExecutionContext().getLong(key)
                : 0L;
    }

    private String formatFailureMessages(StepExecution stepExecution) {
        return stepExecution.getFailureExceptions().stream()
                .map(throwable -> {
                    String message = throwable.getMessage();
                    return message == null ? throwable.getClass().getSimpleName() : message;
                })
                .collect(Collectors.joining(", "));
    }

    private String formatDuration(StepExecution stepExecution) {
        if (stepExecution.getStartTime() == null || stepExecution.getEndTime() == null) {
            return "-";
        }

        Duration duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime());
        long totalSeconds = duration.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0) {
            return minutes + "분 " + seconds + "초";
        }
        return seconds + "초";
    }
}
