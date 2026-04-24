package com.infalce.batch.domain.youtube.batch.listener.model;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.ExecutionContext;

public final class YoutubeBatchStepExecutionContext {

    public static final String CATEGORY_SYNC_CHANGED_COUNT = "categorySync.changedCount";
    public static final String CATEGORY_SYNC_NEW_COUNT = "categorySync.newCount";
    public static final String CHANNEL_DISCOVERY_CATEGORY_COUNT = "channelDiscovery.categoryCount";
    public static final String CHANNEL_DISCOVERY_DISCOVERED_CHANNEL_COUNT = "channelDiscovery.discoveredChannelCount";
    public static final String CHANNEL_METRICS_CHANNEL_COUNT = "channelMetrics.channelCount";
    public static final String CHANNEL_METRICS_VIDEO_COUNT = "channelMetrics.videoCount";

    private YoutubeBatchStepExecutionContext() {
    }

    public static void addLong(String key, long delta) {
        StepContext stepContext = StepSynchronizationManager.getContext();
        if (stepContext == null) {
            return;
        }

        StepExecution stepExecution = stepContext.getStepExecution();
        ExecutionContext executionContext = stepExecution.getExecutionContext();
        long current = executionContext.containsKey(key) ? executionContext.getLong(key) : 0L;
        executionContext.putLong(key, current + delta);
    }
}
